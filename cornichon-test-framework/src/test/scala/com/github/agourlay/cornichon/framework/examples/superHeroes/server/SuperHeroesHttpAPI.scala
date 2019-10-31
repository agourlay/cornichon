package com.github.agourlay.cornichon.framework.examples.superHeroes.server

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }
import cats.implicits._
import com.github.agourlay.cornichon.framework.examples.HttpServer
import io.circe.{ Encoder, Json, JsonObject }
import io.circe.generic.auto._
import io.circe.syntax._
import monix.eval.Task
import monix.eval.Task._
import monix.execution.{ CancelableFuture, Scheduler }
import org.http4s.server.{ AuthMiddleware, Router }
import org.http4s.server.blaze.BlazeServerBuilder
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
import scala.util.{ Failure, Success }

class SuperHeroesHttpAPI() extends Http4sDsl[Task] {

  private val sm = new SuperMicroService()
  implicit val s = Scheduler.Implicits.global

  implicit val heroJsonDecoder = jsonOf[Task, SuperHero]
  implicit val publisherJsonDecoder = jsonOf[Task, Publisher]

  object SessionIdQueryParamMatcher extends QueryParamDecoderMatcher[String]("sessionId")
  object ProtectIdentityQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("protectIdentity")
  object JustNameQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("justName")

  private def validatedJsonResponse[A: Encoder](s: Json => Task[Response[Task]])(v: Validated[ApiError, A]): Task[Response[Task]] =
    v match {
      case Valid(a)   => s(a.asJson)
      case Invalid(e) => apiErrorResponse(e)
    }

  private def apiErrorResponse(e: ApiError): Task[Response[Task]] =
    e match {
      case SessionNotFound(_)        => NotFound(HttpError(e.msg).asJson)
      case PublisherNotFound(_)      => NotFound(HttpError(e.msg).asJson)
      case SuperHeroNotFound(_)      => NotFound(HttpError(e.msg).asJson)
      case PublisherAlreadyExists(_) => Conflict(HttpError(e.msg).asJson)
      case SuperHeroAlreadyExists(_) => Conflict(HttpError(e.msg).asJson)
    }

  private val sessionService = HttpRoutes.of[Task] {
    case POST -> Root / "session" =>
      val sessionId = sm.createSession()
      Created(sessionId)
    case DELETE -> Root / "session" :? SessionIdQueryParamMatcher(sessionId) =>
      sm.deleteSession(sessionId) match {
        case Valid(_)   => Ok()
        case Invalid(e) => apiErrorResponse(e)
      }
  }

  private val publishersService = HttpRoutes.of[Task] {
    case GET -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) =>
      Ok(sm.allPublishers(sessionId).asJson)

    case GET -> Root / "publishers" / name :? SessionIdQueryParamMatcher(sessionId) =>
      validatedJsonResponse(Ok(_))(sm.publisherByName(sessionId, name))

    case req @ POST -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) =>
      for {
        p <- req.as[Publisher]
        created <- Task.delay(sm.addPublisher(sessionId, p))
        resp <- validatedJsonResponse(Ok(_))(created)
      } yield resp
  }

  private val superHeroesService = HttpRoutes.of[Task] {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) =>
      Ok(sm.allSuperheroes(sessionId).asJson)

    case GET -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) :? ProtectIdentityQueryParamMatcher(protectIdentity) =>
      validatedJsonResponse(Ok(_))(sm.superheroByName(sessionId, name, protectIdentity.getOrElse(false)))

    case DELETE -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) =>
      validatedJsonResponse(Ok(_))(sm.deleteSuperhero(sessionId, name))
  }

  private val authStore: BasicAuthenticator[Task, String] = (creds: BasicCredentials) =>
    if (creds.username == "admin" && creds.password == "cornichon")
      Task.now(Some(creds.username))
    else
      Task.now(None)

  private val authMiddleware: AuthMiddleware[Task, String] = BasicAuth("secure site", authStore)

  private val securedSuperHeroesService = authMiddleware {
    AuthedRoutes.of[String, Task] {
      case req @ POST -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ =>
        for {
          s <- req.req.as[SuperHero]
          created <- Task.delay(sm.addSuperhero(sessionId, s))
          resp <- validatedJsonResponse(Created(_))(created)
        } yield resp

      case req @ PUT -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ =>
        for {
          s <- req.req.as[SuperHero]
          updated <- Task.delay(sm.updateSuperhero(sessionId, s))
          resp <- validatedJsonResponse(Ok(_))(updated)
        } yield resp
    }
  }

  private val gqlService = HttpRoutes.of[Task] {
    case req @ POST -> Root =>
      req.as[Json].flatMap { requestJson =>

        val obj = requestJson.asObject
        val query = obj.flatMap(_("query")).flatMap(_.asString)
        val operation = obj.flatMap(_("operationName")).flatMap(_.asString)
        val vars = obj.flatMap(_("variables")).getOrElse(Json.fromJsonObject(JsonObject.empty))
        query.fold(BadRequest(Json.obj("error" → Json.fromString("Query is required")))) { q =>
          QueryParser.parse(q) match {

            // can't parse GraphQL query, return error
            case Failure(error) =>
              BadRequest(Json.obj("error" → Json.fromString(error.getMessage)))

            // query parsed successfully, time to execute it!
            case Success(queryAst) =>
              val f: Future[Json] = Executor.execute(
                schema = GraphQlSchema.SuperHeroesSchema,
                queryAst = queryAst,
                root = new GraphQLSuperMicroService(sm),
                variables = vars,
                operationName = operation
              )

              Task.fromFuture(f)
                .flatMap(a => Ok(a))
                .onErrorHandleWith {
                  case e: QueryAnalysisError => BadRequest(e.resolveError)
                  case e: ErrorWithResolver  => InternalServerError(e.resolveError)
                }
          }
        }
      }
  }

  private val sseSuperHeroesService = HttpRoutes.of[Task] {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) :? JustNameQueryParamMatcher(justNameOpt) =>
      val superheroes = sm.allSuperheroes(sessionId)
      val sse = if (justNameOpt.getOrElse(false))
        superheroes.map(sh => ServerSentEvent(eventType = Some("superhero name"), data = sh.name))
      else
        superheroes.map(sh => ServerSentEvent(eventType = Some("superhero"), data = sh.asJson.noSpaces))
      Ok(fs2.Stream.fromIterator[Task](sse.toIterator))
  }

  private val routes = Router(
    "/" -> (sessionService <+> publishersService <+> superHeroesService <+> securedSuperHeroesService),
    "/sseStream" -> sseSuperHeroesService,
    "/graphql" -> gqlService
  )

  def start(httpPort: Int): CancelableFuture[HttpServer] =
    BlazeServerBuilder[Task]
      .bindHttp(httpPort, "localhost")
      .withoutBanner
      .withNio2(true)
      .withHttpApp(GZip(routes.orNotFound))
      .allocated
      .map { case (_, stop) => new HttpServer(stop) }
      .runToFuture
}