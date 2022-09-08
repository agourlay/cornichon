package com.github.agourlay.cornichon.framework.examples.superHeroes.server

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }
import sangria.macros.derive._
import sangria.schema.Schema
import sangria.schema._
import sangria.marshalling.circe._
import io.circe.generic.auto._

class GraphQLSuperMicroService(sm: SuperMicroService) {

  def publisherByName(sessionId: String, name: String): Option[Publisher] =
    unpack(sm.publisherByName(sessionId, name))

  def superheroByName(sessionId: String, name: String, protectIdentity: Boolean = false): Option[SuperHero] =
    unpack(sm.superheroByName(sessionId, name, protectIdentity))

  def updateSuperhero(sessionId: String, s: SuperHero): Option[SuperHero] =
    unpack(sm.updateSuperhero(sessionId, s))

  private def unpack[A](v: Validated[ApiError, A]): Option[A] =
    v match {
      case Valid(p) => Some(p)
      case Invalid(e) => e match {
        case SessionNotFound(_)   => None
        case PublisherNotFound(_) => None
        case SuperHeroNotFound(_) => None
        case _                    => throw new RuntimeException(e.msg)
      }
    }
}

object GraphQlSchema {

  implicit val PublisherType: ObjectType[Unit, Publisher] = deriveObjectType[Unit, Publisher](
    ObjectTypeDescription("A comics publisher.")
  )

  implicit val SuperHeroType: ObjectType[Unit, SuperHero] = deriveObjectType[Unit, SuperHero](
    ObjectTypeDescription("A superhero.")
  )

  implicit val PublisherInputType: InputObjectType[Publisher] = deriveInputObjectType[Publisher](
    InputObjectTypeName("PublisherInput")
  )

  implicit val SuperHeroInputType: InputObjectType[SuperHero] = deriveInputObjectType[SuperHero](
    InputObjectTypeName("SuperHeroInput")
  )

  val QueryType: ObjectType[Unit, GraphQLSuperMicroService] = deriveObjectType[Unit, GraphQLSuperMicroService](
    ObjectTypeName("Root"),
    ObjectTypeDescription("Gateway to awesomeness."),
    IncludeMethods("publisherByName", "superheroByName")
  )

  val MutationType: ObjectType[Unit, GraphQLSuperMicroService] = deriveObjectType[Unit, GraphQLSuperMicroService](
    ObjectTypeName("RootMut"),
    ObjectTypeDescription("Gateway to mutation awesomeness!"),
    IncludeMethods("updateSuperhero")
  )

  val SuperHeroesSchema: Schema[Unit, GraphQLSuperMicroService] = Schema(QueryType, Some(MutationType))
}
