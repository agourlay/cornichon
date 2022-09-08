package com.github.agourlay.cornichon.framework.examples.superHeroes.server

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.comcast.ip4s.{ Host, Port }
import com.github.agourlay.cornichon.framework.examples.HttpServer
import fs2.Stream
import io.circe.{ Encoder, Json, JsonObject }
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.server.{ AuthMiddleware, Router }
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.middleware.authentication.BasicAuth.BasicAuthenticator
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.server.middleware.GZip
import sangria.execution._
import sangria.parser.QueryParser
import sangria.marshalling.circe._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class SuperHeroesHttpAPI() extends Http4sDsl[IO] {

  private val sm = new SuperMicroService()
  private implicit val ec = scala.concurrent.ExecutionContext.global

  implicit val heroJsonDecoder = jsonOf[IO, SuperHero]
  implicit val publisherJsonDecoder = jsonOf[IO, Publisher]

  object SessionIdQueryParamMatcher extends QueryParamDecoderMatcher[String]("sessionId")
  object ProtectIdentityQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("protectIdentity")
  object JustNameQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("justName")

  private def validatedJsonResponse[A: Encoder](s: Json => IO[Response[IO]])(v: Validated[ApiError, A]): IO[Response[IO]] =
    v match {
      case Valid(a)   => s(a.asJson)
      case Invalid(e) => apiErrorResponse(e)
    }

  private def apiErrorResponse(e: ApiError): IO[Response[IO]] =
    e match {
      case SessionNotFound(_)        => NotFound(HttpError(e.msg).asJson)
      case PublisherNotFound(_)      => NotFound(HttpError(e.msg).asJson)
      case SuperHeroNotFound(_)      => NotFound(HttpError(e.msg).asJson)
      case PublisherAlreadyExists(_) => Conflict(HttpError(e.msg).asJson)
      case SuperHeroAlreadyExists(_) => Conflict(HttpError(e.msg).asJson)
    }

  private val sessionService = HttpRoutes.of[IO] {
    case POST -> Root / "session" =>
      val sessionId = sm.createSession()
      Created(sessionId)
    case DELETE -> Root / "session" :? SessionIdQueryParamMatcher(sessionId) =>
      sm.deleteSession(sessionId) match {
        case Valid(_)   => Ok()
        case Invalid(e) => apiErrorResponse(e)
      }
  }

  private val publishersService = HttpRoutes.of[IO] {
    case GET -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) =>
      Ok(sm.allPublishers(sessionId).asJson)

    case GET -> Root / "publishers" / name :? SessionIdQueryParamMatcher(sessionId) =>
      validatedJsonResponse(Ok(_))(sm.publisherByName(sessionId, name))

    case req @ POST -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) =>
      for {
        p <- req.as[Publisher]
        created <- IO.delay(sm.addPublisher(sessionId, p))
        resp <- validatedJsonResponse(Ok(_))(created)
      } yield resp
  }

  private val superHeroesService = HttpRoutes.of[IO] {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) =>
      Ok(sm.allSuperheroes(sessionId).asJson)

    case GET -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) :? ProtectIdentityQueryParamMatcher(protectIdentity) =>
      validatedJsonResponse(Ok(_))(sm.superheroByName(sessionId, name, protectIdentity.getOrElse(false)))

    case DELETE -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) =>
      validatedJsonResponse(Ok(_))(sm.deleteSuperhero(sessionId, name))
  }

  private val authStore: BasicAuthenticator[IO, String] = (creds: BasicCredentials) =>
    if (creds.username == "admin" && creds.password == "cornichon")
      IO.pure(Some(creds.username))
    else
      IO.pure(None)

  private val authMiddleware: AuthMiddleware[IO, String] = BasicAuth("secure site", authStore)

  private val securedSuperHeroesService = authMiddleware {
    AuthedRoutes.of[String, IO] {
      case req @ POST -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ =>
        for {
          s <- req.req.as[SuperHero]
          created <- IO.delay(sm.addSuperhero(sessionId, s))
          resp <- validatedJsonResponse(Created(_))(created)
        } yield resp

      case req @ PUT -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ =>
        for {
          s <- req.req.as[SuperHero]
          updated <- IO.delay(sm.updateSuperhero(sessionId, s))
          resp <- validatedJsonResponse(Ok(_))(updated)
        } yield resp
    }
  }

  private val gqlService = HttpRoutes.of[IO] {
    case req @ POST -> Root =>
      req.as[Json].flatMap { requestJson =>

        val obj = requestJson.asObject
        val query = obj.flatMap(_("query")).flatMap(_.asString)
        val operation = obj.flatMap(_("operationName")).flatMap(_.asString)
        val vars = obj.flatMap(_("variables")).getOrElse(Json.fromJsonObject(JsonObject.empty))
        query.fold(BadRequest(Json.obj("error" -> Json.fromString("Query is required")))) { q =>
          QueryParser.parse(q) match {

            // can't parse GraphQL query, return error
            case Failure(error) =>
              BadRequest(Json.obj("error" -> Json.fromString(error.getMessage)))

            // query parsed successfully, time to execute it!
            case Success(queryAst) =>
              val f: Future[Json] = Executor.execute(
                schema = GraphQlSchema.SuperHeroesSchema,
                queryAst = queryAst,
                root = new GraphQLSuperMicroService(sm),
                variables = vars,
                operationName = operation
              )

              IO.fromFuture(IO.delay(f))
                .flatMap(a => Ok(a))
                .handleErrorWith {
                  case e: QueryAnalysisError => BadRequest(e.resolveError)
                  case e: ErrorWithResolver  => InternalServerError(e.resolveError)
                }
          }
        }
      }
  }

  private val sseSuperHeroesService = HttpRoutes.of[IO] {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) :? JustNameQueryParamMatcher(justNameOpt) =>
      val superheroes = sm.allSuperheroes(sessionId)
      val sse = if (justNameOpt.getOrElse(false))
        superheroes.map(sh => ServerSentEvent(eventType = Some("superhero name"), data = Some(sh.name)))
      else
        superheroes.map(sh => ServerSentEvent(eventType = Some("superhero"), data = Some(sh.asJson.noSpaces)))
      Ok(Stream.iterable[IO, ServerSentEvent](sse))
  }

  private val routes = Router(
    "/" -> (sessionService <+> publishersService <+> superHeroesService <+> securedSuperHeroesService),
    "/sseStream" -> sseSuperHeroesService,
    "/graphql" -> gqlService
  )

  def start(httpPort: Int): Future[HttpServer] =
    Port.fromInt(httpPort) match {
      case None => Future.failed(new IllegalArgumentException("Invalid port number"))
      case Some(port) =>
        EmberServerBuilder.default[IO]
          .withPort(port)
          .withHost(Host.fromString("localhost").get)
          .withHttpApp(GZip(routes.orNotFound))
          .withShutdownTimeout(1.seconds)
          .build
          .allocated
          .map { case (_, stop) => new HttpServer(stop) }
          .unsafeToFuture()
    }
}