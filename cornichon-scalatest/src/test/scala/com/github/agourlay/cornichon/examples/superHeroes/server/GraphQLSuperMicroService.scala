package com.github.agourlay.cornichon.examples.superHeroes.server

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }
import sangria.macros.derive._
import sangria.schema.Schema
import sangria.macros.derive._
import sangria.schema.Schema
import sangria.schema._
import sangria.marshalling.circe._
import io.circe.generic.auto._
import io.circe.syntax._

class GraphQLSuperMicroService(sm: SuperMicroService) {

  def publisherByName(sessionId: String, name: String): Publisher =
    unpack(sm.publisherByName(sessionId, name))

  def superheroByName(sessionId: String, name: String, protectIdentity: Boolean = false): SuperHero =
    unpack(sm.superheroByName(sessionId, name, protectIdentity))

  def updateSuperhero(sessionId: String, s: SuperHero): SuperHero =
    unpack(sm.updateSuperhero(sessionId, s))

  private def unpack[A](v: Validated[ApiError, A]): A =
    v match {
      case Invalid(e) ⇒ throw new RuntimeException(e.msg)
      case Valid(p)   ⇒ p
    }
}

object GraphQlSchema {

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

  val QueryType = deriveObjectType[Unit, GraphQLSuperMicroService](
    ObjectTypeName("Root"),
    ObjectTypeDescription("Gateway to awesomeness."),
    IncludeMethods("publisherByName", "superheroByName")
  )

  val MutationType = deriveObjectType[Unit, GraphQLSuperMicroService](
    ObjectTypeName("RootMut"),
    ObjectTypeDescription("Gateway to mutation awesomeness!"),
    IncludeMethods("updateSuperhero")
  )

  val SuperHeroesSchema = Schema(QueryType, Some(MutationType))
}

