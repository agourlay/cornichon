package com.github.agourlay.cornichon.examples.superHeroes.server

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.heikoseeberger.akkasse.ServerSentEvent
import spray.json.DefaultJsonProtocol
import sangria.macros.derive._
import sangria.schema.Schema

case class Publisher(name: String, foundationYear: Int, location: String)

case class SuperHero(name: String, realName: String, city: String, hasSuperpowers: Boolean, publisher: Publisher)

import sangria.schema._
import sangria.marshalling.sprayJson._

object GraphQlSchema {
  import JsonSupport._

  implicit val PublisherType = deriveObjectType[Unit, Publisher](
    ObjectTypeDescription("A comics publisher.")
  )

  implicit val SuperHeroType = deriveObjectType[Unit, SuperHero](
    ObjectTypeDescription("A superhero.")
  )

  implicit val PublisherInputType = deriveInputObjectType[Publisher](
    InputObjectTypeName("PublisherInput")
  )

  implicit val SuperHeroInputType = deriveInputObjectType[SuperHero](
    InputObjectTypeName("SuperHeroInput")
  )

  val QueryType = deriveObjectType[Unit, TestData](
    ObjectTypeName("Root"),
    ObjectTypeDescription("Gateway to awesomeness."),
    IncludeMethods("publisherByName", "superheroByName")
  )

  val MutationType = deriveObjectType[Unit, TestData](
    ObjectTypeName("RootMut"),
    ObjectTypeDescription("Gateway to mutation awesomeness!"),
    IncludeMethods("updateSuperhero")
  )

  val SuperHeroesSchema = Schema(QueryType, Some(MutationType))
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

object JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val formatCP = jsonFormat3(Publisher)
  implicit val formatSH = jsonFormat5(SuperHero)
  implicit val formatHE = jsonFormat1(HttpError)
  implicit def toServerSentEvent(sh: SuperHero): ServerSentEvent = {
    ServerSentEvent(eventType = "superhero", data = formatSH.write(sh).compactPrint)
  }
}