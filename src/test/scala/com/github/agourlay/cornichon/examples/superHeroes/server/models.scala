package com.github.agourlay.cornichon.examples.superHeroes.server

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.heikoseeberger.akkasse.ServerSentEvent
import spray.json.DefaultJsonProtocol
import sangria.macros.derive._
import sangria.schema.Schema

case class Publisher(name: String, foundationYear: Int, location: String)

case class SuperHero(name: String, realName: String, city: String, hasSuperpowers: Boolean, publisher: Publisher)

object GraphQlSchema {

  implicit val PublisherType = deriveObjectType[Unit, Publisher](
    ObjectTypeName("Publisher"),
    ObjectTypeDescription("A comics publisher.")
  )

  implicit val SuperHeroType = deriveObjectType[Unit, SuperHero](
    ObjectTypeName("Superhero"),
    ObjectTypeDescription("A superhero.")
  )

  val QueryType = deriveObjectType[Unit, TestData](
    ObjectTypeName("Root"),
    ObjectTypeDescription("Gateway to awesomeness.")
  )

  val SuperHeroesSchema = Schema(QueryType)
}

trait ResourceNotFound extends Exception {
  def id: String
}

case class SessionNotFound(id: String) extends ResourceNotFound
case class PublisherNotFound(id: String) extends ResourceNotFound
case class SuperHeroNotFound(id: String) extends ResourceNotFound

trait ResourceAlreadyExists extends Exception {
  def id: String
}
case class PublisherAlreadyExists(id: String) extends ResourceNotFound
case class SuperHeroAlreadyExists(id: String) extends ResourceNotFound

case class HttpError(error: String)

trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val formatCP = jsonFormat3(Publisher)
  implicit val formatSH = jsonFormat5(SuperHero)
  implicit val formatHE = jsonFormat1(HttpError)
  implicit def toServerSentEvent(sh: SuperHero): ServerSentEvent = {
    ServerSentEvent(eventType = "superhero", data = formatSH.write(sh).compactPrint)
  }
}