package com.github.agourlay.cornichon.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server._
import Directives._

import scala.concurrent.ExecutionContext

class RestAPI() extends JsonSupport {

  implicit val system = ActorSystem("testData-http-server")
  implicit val mat = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  val testData = new TestData()

  implicit val exceptionHandler = ExceptionHandler {

    case e: PublisherNotFound ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(NotFound -> HttpError(s"Publisher ${e.id} not found")))
      }

    case e: PublisherAlreadyExists ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(Conflict -> HttpError(s"Publisher ${e.id} already exist")))
      }

    case e: SuperHeroNotFound ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(NotFound -> HttpError(s"Superhero ${e.id} not found")))
      }

    case e: SuperHeroAlreadyExists ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(Conflict -> HttpError(s"Superhero ${e.id} already exist")))
      }

    case e: Exception ⇒
      extractUri { uri ⇒
        complete(ToResponseMarshallable(InternalServerError -> HttpError("An unexpected error occured")))
      }
  }

  val route: Route =
    path("publishers") {
      get {
        onSuccess(testData.allPublishers) { publishers: Seq[Publisher] ⇒
          complete(ToResponseMarshallable(OK -> publishers))
        }
      } ~
        post {
          entity(as[Publisher]) { p: Publisher ⇒
            onSuccess(testData.addPublisher(p)) { created: Publisher ⇒
              complete(ToResponseMarshallable(Created -> created))
            }
          }
        }
    } ~
      path("publishers" / Rest) { name: String ⇒
        get {
          onSuccess(testData.publisherByName(name)) { pub: Publisher ⇒
            complete(ToResponseMarshallable(OK -> pub))
          }
        }
      } ~
      path("superheroes") {
        get {
          onSuccess(testData.allSuperheroes) { superheroes: Seq[SuperHero] ⇒
            complete(ToResponseMarshallable(OK -> superheroes))
          }
        } ~
          post {
            entity(as[SuperHero]) { s: SuperHero ⇒
              onSuccess(testData.addSuperhero(s)) { created: SuperHero ⇒
                complete(ToResponseMarshallable(Created -> created))
              }
            }
          } ~
          put {
            entity(as[SuperHero]) { s: SuperHero ⇒
              onSuccess(testData.updateSuperhero(s)) { updated: SuperHero ⇒
                complete(ToResponseMarshallable(OK -> updated))
              }
            }
          }
      } ~
      path("superheroes" / Rest) { name: String ⇒
        get {
          parameters('protectIdentity ? false) { protectIdentity: Boolean ⇒
            onSuccess(testData.superheroByName(name, protectIdentity)) { s: SuperHero ⇒
              complete(ToResponseMarshallable(OK -> s))
            }
          }

        } ~
          delete {
            onSuccess(testData.deleteSuperhero(name)) { s: SuperHero ⇒
              complete(ToResponseMarshallable(OK -> s))
            }
          }
      }

  def start(httpPort: Int) = Http(system).bindAndHandle(route, "localhost", port = httpPort)
}
