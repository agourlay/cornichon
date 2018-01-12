package com.github.agourlay.cornichon.examples.superHeroes.server

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }
import cats.syntax.semigroup._
import cats._
import data._
import implicits._
import io.circe.{ Decoder, Encoder, Json, JsonObject }
import io.circe.generic.auto._
import io.circe.syntax._
import monix.eval.Task
import monix.eval.Task._
import monix.execution.Scheduler
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.middleware.authentication.BasicAuth.BasicAuthenticator
import org.http4s.{ AuthedService, _ }
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.server.middleware.GZip
import org.http4s.implicits._
import sangria.execution._
import sangria.parser.QueryParser
import sangria.marshalling.circe._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

class HttpAPI() extends Http4sDsl[Task] {

  val sm = new SuperMicroService()
  implicit val s = Scheduler.Implicits.global

  implicit def circeJsonDecoder[A: Decoder]: EntityDecoder[Task, A] = jsonOf[Task, A]

  object SessionIdQueryParamMatcher extends QueryParamDecoderMatcher[String]("sessionId")
  object ProtectIdentityQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("protectIdentity")
  object JustNameQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("justName")

  def validatedJsonResponse[A: Encoder](s: Json ⇒ Task[Response[Task]])(v: Validated[ApiError, A]): Task[Response[Task]] =
    v match {
      case Valid(a)   ⇒ s(a.asJson)
      case Invalid(e) ⇒ apiErrorResponse(e)
    }

  def apiErrorResponse(e: ApiError): Task[Response[Task]] =
    e match {
      case SessionNotFound(_)        ⇒ NotFound(HttpError(e.msg).asJson)
      case PublisherNotFound(_)      ⇒ NotFound(HttpError(e.msg).asJson)
      case SuperHeroNotFound(_)      ⇒ NotFound(HttpError(e.msg).asJson)
      case PublisherAlreadyExists(_) ⇒ Conflict(HttpError(e.msg).asJson)
      case SuperHeroAlreadyExists(_) ⇒ Conflict(HttpError(e.msg).asJson)
    }

  val sessionService: HttpService[Task] = HttpService[Task] {
    case POST -> Root / "session" ⇒
      val sessionId = sm.createSession()
      Created(sessionId)
    case DELETE -> Root / "session" :? SessionIdQueryParamMatcher(sessionId) ⇒
      sm.deleteSession(sessionId) match {
        case Valid(_)   ⇒ Ok()
        case Invalid(e) ⇒ apiErrorResponse(e)
      }
  }

  val publishersService: HttpService[Task] = HttpService[Task] {
    case GET -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) ⇒
      Ok(sm.allPublishers(sessionId).asJson)

    case GET -> Root / "publishers" / name :? SessionIdQueryParamMatcher(sessionId) ⇒
      validatedJsonResponse(Ok(_))(sm.publisherByName(sessionId, name))

    case req @ POST -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) ⇒
      for {
        p ← req.as[Publisher]
        created ← Task.delay(sm.addPublisher(sessionId, p))
        resp ← validatedJsonResponse(Ok(_))(created)
      } yield resp
  }

  val superHeroesService: HttpService[Task] = HttpService[Task] {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) ⇒
      Ok(sm.allSuperheroes(sessionId).asJson)

    case GET -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) :? ProtectIdentityQueryParamMatcher(protectIdentity) ⇒
      validatedJsonResponse(Ok(_))(sm.superheroByName(sessionId, name, protectIdentity.getOrElse(false)))

    case DELETE -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) ⇒
      validatedJsonResponse(Ok(_))(sm.deleteSuperhero(sessionId, name))
  }

  val authStore: BasicAuthenticator[Task, String] = (creds: BasicCredentials) ⇒
    if (creds.username == "admin" && creds.password == "cornichon")
      Task.now(Some(creds.username))
    else
      Task.now(None)

  val securedSuperHeroesService = BasicAuth("secure site", authStore)(AuthedService[String, Task] {
    case req @ POST -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ ⇒
      for {
        s ← req.req.as[SuperHero]
        created ← Task.delay(sm.addSuperhero(sessionId, s))
        resp ← validatedJsonResponse(Created(_))(created)
      } yield resp

    case req @ PUT -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ ⇒
      for {
        s ← req.req.as[SuperHero]
        updated ← Task.delay(sm.updateSuperhero(sessionId, s))
        resp ← validatedJsonResponse(Ok(_))(updated)
      } yield resp
  })

  val gqlService: HttpService[Task] = HttpService[Task] {
    case req @ POST -> Root ⇒
      req.as[Json].flatMap { requestJson ⇒

        val obj = requestJson.asObject
        val query = obj.flatMap(_("query")).flatMap(_.asString)
        val operation = obj.flatMap(_("operationName")).flatMap(_.asString)
        val vars = obj.flatMap(_("variables")).getOrElse(Json.fromJsonObject(JsonObject.empty))
        query.fold(BadRequest(Json.obj("error" → Json.fromString("Query is required")))) { q ⇒
          QueryParser.parse(q) match {

            // can't parse GraphQL query, return error
            case Failure(error) ⇒
              BadRequest(Json.obj("error" → Json.fromString(error.getMessage)))

            // query parsed successfully, time to execute it!
            case Success(queryAst) ⇒
              val f: Future[Json] = Executor.execute(
                schema = GraphQlSchema.SuperHeroesSchema,
                queryAst = queryAst,
                root = new GraphQLSuperMicroService(sm),
                variables = vars,
                operationName = operation
              )

              Task.fromFuture(f)
                .flatMap(a ⇒ Ok(a))
                .onErrorHandleWith {
                  case e: QueryAnalysisError ⇒ BadRequest(e.resolveError)
                  case e: ErrorWithResolver  ⇒ InternalServerError(e.resolveError)
                }
          }
        }
      }
  }

  val sseSuperHeroesService: HttpService[Task] = HttpService[Task] {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) :? JustNameQueryParamMatcher(justNameOpt) ⇒
      val superheroes = sm.allSuperheroes(sessionId)
      val sse = if (justNameOpt.getOrElse(false))
        superheroes.map(sh ⇒ ServerSentEvent(eventType = Some("superhero name"), data = sh.name))
      else
        superheroes.map(sh ⇒ ServerSentEvent(eventType = Some("superhero"), data = sh.asJson.noSpaces))
      Ok(Stream[ServerSentEvent](sse.toSeq: _*))
  }

  val services = GZip[Task](sessionService <+> publishersService <+> superHeroesService <+> securedSuperHeroesService)

  def start(httpPort: Int) =
    BlazeBuilder[Task]
      .bindHttp(httpPort, "localhost")
      .mountService(services, "/")
      .mountService(sseSuperHeroesService, "/sseStream")
      .mountService(gqlService, "/graphql")
      .start
      .map(new HttpServer(_))
      .runAsync
}

class HttpServer(server: Server[Task])(implicit s: Scheduler) {
  def shutdown() = server.shutdown.runAsync
}