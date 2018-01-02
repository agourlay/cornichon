package com.github.agourlay.cornichon.examples.superHeroes.server

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }

import cats.syntax.semigroup._
import fs2.{ Strategy, Task, Stream }
import io.circe.{ Decoder, Encoder, Json, JsonObject }
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.middleware.authentication.BasicAuth.BasicAuthenticator
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.server.middleware.GZip

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success }
import sangria.execution._
import sangria.parser.QueryParser
import sangria.marshalling.circe._

class HttpAPI() {

  implicit val strategy = Strategy.fromExecutionContext(ExecutionContext.Implicits.global)
  val sm = new SuperMicroService()

  implicit def circeJsonDecoder[A: Decoder]: EntityDecoder[A] = jsonOf[A]

  object SessionIdQueryParamMatcher extends QueryParamDecoderMatcher[String]("sessionId")
  object ProtectIdentityQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("protectIdentity")
  object JustNameQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("justName")

  def validatedJsonReponse[A: Encoder](s: Json ⇒ Task[Response])(v: Validated[ApiError, A]) =
    v match {
      case Valid(a)   ⇒ s(a.asJson)
      case Invalid(e) ⇒ apiErrorResponse(e)
    }

  def apiErrorResponse(e: ApiError): Task[Response] =
    e match {
      case SessionNotFound(_)        ⇒ NotFound(HttpError(e.msg).asJson)
      case PublisherNotFound(_)      ⇒ NotFound(HttpError(e.msg).asJson)
      case SuperHeroNotFound(_)      ⇒ NotFound(HttpError(e.msg).asJson)
      case PublisherAlreadyExists(_) ⇒ Conflict(HttpError(e.msg).asJson)
      case SuperHeroAlreadyExists(_) ⇒ Conflict(HttpError(e.msg).asJson)
    }

  val sessionService = HttpService {
    case POST -> Root / "session" ⇒
      val sessionId = sm.createSession()
      Created(sessionId)
    case DELETE -> Root / "session" :? SessionIdQueryParamMatcher(sessionId) ⇒
      sm.deleteSession(sessionId) match {
        case Valid(_)   ⇒ Ok()
        case Invalid(e) ⇒ apiErrorResponse(e)
      }
  }

  val publishersService = HttpService {
    case GET -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) ⇒
      Ok(sm.allPublishers(sessionId).asJson)

    case GET -> Root / "publishers" / name :? SessionIdQueryParamMatcher(sessionId) ⇒
      validatedJsonReponse(Ok(_))(sm.publisherByName(sessionId, name))

    case req @ POST -> Root / "publishers" :? SessionIdQueryParamMatcher(sessionId) ⇒
      for {
        p ← req.as[Publisher]
        created ← Task.delay(sm.addPublisher(sessionId, p))
        resp ← validatedJsonReponse(Ok(_))(created)
      } yield resp
  }

  val superHeroesService = HttpService {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) ⇒
      Ok(sm.allSuperheroes(sessionId).asJson)

    case GET -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) :? ProtectIdentityQueryParamMatcher(protectIdentity) ⇒
      validatedJsonReponse(Ok(_))(sm.superheroByName(sessionId, name, protectIdentity.getOrElse(false)))

    case DELETE -> Root / "superheroes" / name :? SessionIdQueryParamMatcher(sessionId) ⇒
      validatedJsonReponse(Ok(_))(sm.deleteSuperhero(sessionId, name))
  }

  val authStore: BasicAuthenticator[String] = (creds: BasicCredentials) ⇒
    if (creds.username == "admin" && creds.password == "cornichon")
      Task.now(Some(creds.username))
    else
      Task.now(None)

  val securedSuperHeroesService: HttpService = BasicAuth("secure site", authStore)(AuthedService[String] {
    case req @ POST -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ ⇒
      for {
        s ← req.req.as[SuperHero]
        created ← Task.delay(sm.addSuperhero(sessionId, s))
        resp ← validatedJsonReponse(Created(_))(created)
      } yield resp

    case req @ PUT -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) as _ ⇒
      for {
        s ← req.req.as[SuperHero]
        updated ← Task.delay(sm.updateSuperhero(sessionId, s))
        resp ← validatedJsonReponse(Ok(_))(updated)
      } yield resp
  })

  val gqlService = HttpService {
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
                .handleWith {
                  case e: QueryAnalysisError ⇒ BadRequest(e.resolveError)
                  case e: ErrorWithResolver  ⇒ InternalServerError(e.resolveError)
                }
          }
        }
      }
  }

  val sseSuperHeroesService = HttpService {
    case GET -> Root / "superheroes" :? SessionIdQueryParamMatcher(sessionId) :? JustNameQueryParamMatcher(justNameOpt) ⇒
      val superheroes = sm.allSuperheroes(sessionId)
      val sse = if (justNameOpt.getOrElse(false))
        superheroes.map(sh ⇒ ServerSentEvent(eventType = Some("superhero name"), data = sh.name))
      else
        superheroes.map(sh ⇒ ServerSentEvent(eventType = Some("superhero"), data = sh.asJson.noSpaces))
      Ok(Stream.emits[Task, ServerSentEvent](sse.toSeq))
  }

  val services = GZip(sessionService |+| publishersService |+| superHeroesService |+| securedSuperHeroesService)

  def start(httpPort: Int) =
    BlazeBuilder
      .bindHttp(httpPort, "localhost")
      .mountService(services, "/")
      .mountService(sseSuperHeroesService, "/sseStream")
      .mountService(gqlService, "/graphql")
      .start
      .map(new HttpServer(_))
      .unsafeRunAsyncFuture()
}

class HttpServer(server: Server) {
  def shutdown() = server.shutdown.unsafeRunAsyncFuture()
}