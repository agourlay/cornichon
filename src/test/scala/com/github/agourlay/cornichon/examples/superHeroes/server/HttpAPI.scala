package com.github.agourlay.cornichon.examples.superHeroes.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Remaining
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.Credentials
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkasse.{ EventStreamMarshalling, ServerSentEvent }
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import sangria.execution._
import sangria.parser.QueryParser
import sangria.marshalling.circe._
import io.circe.{ Json, JsonObject }
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

class HttpAPI() extends EventStreamMarshalling {

  import JsonSupport._

  implicit val system = ActorSystem("testData-http-server")
  implicit val mat = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  val testData = new TestData()

  implicit val exceptionHandler = ExceptionHandler {

    case e: PublisherNotFound ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(NotFound → HttpError(s"Publisher ${e.id} not found")))
      }

    case e: PublisherAlreadyExists ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(Conflict → HttpError(s"Publisher ${e.id} already exist")))
      }

    case e: SuperHeroNotFound ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(NotFound → HttpError(s"Superhero ${e.id} not found")))
      }

    case e: SuperHeroAlreadyExists ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(Conflict → HttpError(s"Superhero ${e.id} already exist")))
      }

    case e: SessionNotFound ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(NotFound → HttpError(s"Session ${e.id} not found")))
      }

    case e: Exception ⇒
      extractUri { uri ⇒
        println(e.printStackTrace())
        complete(ToResponseMarshallable(InternalServerError → HttpError("An unexpected error occured")))
      }
  }

  val route: Route = encodeResponse {
    path("session") {
      post {
        onSuccess(testData.createSession()) { session: String ⇒
          complete(Created → HttpEntity(ContentTypes.`text/html(UTF-8)`, session))
        }
      }
    } ~
      path("publishers") {
        get {
          parameters('sessionId) { sessionId: String ⇒
            onSuccess(testData.allPublishers(sessionId)) { publishers: Seq[Publisher] ⇒
              complete(ToResponseMarshallable(OK → publishers))
            }
          }
        } ~
          post {
            parameters('sessionId) { sessionId: String ⇒
              entity(as[Publisher]) { p: Publisher ⇒
                onSuccess(testData.addPublisher(sessionId, p)) { created: Publisher ⇒
                  complete(ToResponseMarshallable(Created → created))
                }
              }
            }
          }
      } ~
      path("publishers" / Remaining) { name: String ⇒
        get {
          parameters('sessionId) { sessionId: String ⇒
            onSuccess(testData.publisherByName(name, sessionId)) { pub: Publisher ⇒
              complete(ToResponseMarshallable(OK → pub))
            }
          }
        }
      } ~
      path("superheroes") {
        get {
          parameters('sessionId) { sessionId: String ⇒
            onSuccess(testData.allSuperheroes(sessionId)) { superheroes: Seq[SuperHero] ⇒
              complete(ToResponseMarshallable(OK → superheroes))
            }
          }
        } ~
          post {
            authenticateBasicPF(realm = "secure site", login) { userName ⇒
              parameters('sessionId) { sessionId: String ⇒
                entity(as[SuperHero]) { s: SuperHero ⇒
                  onSuccess(testData.addSuperhero(sessionId, s)) { created: SuperHero ⇒
                    complete(ToResponseMarshallable(Created → created))
                  }
                }
              }
            }
          } ~
          put {
            authenticateBasicPF(realm = "secure site", login) { userName ⇒
              entity(as[SuperHero]) { s: SuperHero ⇒
                parameters('sessionId) { sessionId: String ⇒
                  onSuccess(testData.updateSuperhero(sessionId, s)) { updated: SuperHero ⇒
                    complete(ToResponseMarshallable(OK → updated))
                  }
                }
              }
            }
          }
      } ~
      path("superheroes" / Remaining) { name: String ⇒
        get {
          parameters('protectIdentity ? false) { protectIdentity: Boolean ⇒
            parameters('sessionId) { sessionId: String ⇒
              onSuccess(testData.superheroByName(sessionId, name, protectIdentity)) { s: SuperHero ⇒
                complete(ToResponseMarshallable(OK → s))
              }
            }
          }

        } ~
          delete {
            parameters('sessionId) { sessionId: String ⇒
              onSuccess(testData.deleteSuperhero(sessionId, name)) { s: SuperHero ⇒
                complete(ToResponseMarshallable(OK → s))
              }
            }
          }
      } ~
      pathPrefix("sseStream") {
        path("superheroes") {
          get {
            parameters('justName ? false) { justName: Boolean ⇒
              parameters('sessionId) { sessionId: String ⇒
                onSuccess(testData.allSuperheroes(sessionId)) { superheroes: Seq[SuperHero] ⇒
                  complete {
                    if (justName) Source(superheroes.toVector.map(sh ⇒ ServerSentEvent(eventType = "superhero name", data = sh.name)))
                    else Source(superheroes.toVector.map(toServerSentEvent))
                  }
                }
              }
            }
          }
        }
      } ~
      path("graphql") {
        post {
          entity(as[Json]) { requestJson ⇒
            val obj = requestJson.asObject
            val query = obj.flatMap(_("query")).flatMap(_.asString)
            val operation = obj.flatMap(_("operationName")).flatMap(_.asString)
            val vars = obj.flatMap(_("variables")).getOrElse(Json.fromJsonObject(JsonObject.empty))

            query.fold(complete(BadRequest → Json.obj("error" → Json.fromString("Query is required")))) { q ⇒

              QueryParser.parse(q) match {

                // query parsed successfully, time to execute it!
                case Success(queryAst) ⇒
                  complete(
                    Executor.execute(
                      schema = GraphQlSchema.SuperHeroesSchema,
                      queryAst = queryAst,
                      root = testData,
                      variables = vars,
                      operationName = operation
                    ).map(OK → _)
                      .recover {
                        case error: QueryAnalysisError ⇒ BadRequest → error.resolveError
                        case error: ErrorWithResolver  ⇒ InternalServerError → error.resolveError
                      }
                  )

                // can't parse GraphQL query, return error
                case Failure(error) ⇒
                  complete(BadRequest → Json.obj("error" → Json.fromString(error.getMessage)))
              }

            }

          }
        }
      }
  }

  def start(httpPort: Int) = Http(system).bindAndHandle(route, "localhost", port = httpPort)

  def login: PartialFunction[Credentials, String] = {
    case u @ Credentials.Provided(username) if username == "admin" && u.verify("cornichon") ⇒ "admin"
  }
}
