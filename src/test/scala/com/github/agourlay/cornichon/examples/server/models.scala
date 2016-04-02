package com.github.agourlay.cornichon.examples.server

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.heikoseeberger.akkasse.ServerSentEvent
import spray.json.DefaultJsonProtocol

import scala.collection.mutable
import scala.concurrent._
import scala.util.Random

case class Publisher(name: String, foundationYear: Int, location: String)

case class SuperHero(name: String, realName: String, city: String, hasSuperpowers: Boolean, publisher: Publisher)

class TestData(implicit executionContext: ExecutionContext) {

  val publishersBySession = new mutable.HashMap[String, mutable.Set[Publisher]] with mutable.MultiMap[String, Publisher]
  val superheroesBySession = new mutable.HashMap[String, mutable.Set[SuperHero]] with mutable.MultiMap[String, SuperHero]

  def createSession(): Future[String] = Future {
    val newSessionId = Random.alphanumeric.take(6).mkString
    initialPublishers.foreach(publishersBySession.addBinding(newSessionId, _))
    initialSuperheroes.foreach(superheroesBySession.addBinding(newSessionId, _))
    newSessionId
  }

  def publishersBySessionUnsafe(sessionId: String) = publishersBySession.getOrElse(sessionId, throw new SessionNotFound(sessionId))

  def superheroesBySessionUnsafe(sessionId: String) = superheroesBySession.getOrElse(sessionId, throw new SessionNotFound(sessionId))

  def publisherByName(sessionId: String, name: String) = Future {
    publishersBySessionUnsafe(sessionId).find(_.name == name).fold(throw new PublisherNotFound(name)) { c ⇒ c }
  }

  def addPublisher(sessionId: String, p: Publisher) = Future {
    if (publishersBySessionUnsafe(sessionId).exists(_.name == p.name)) throw new PublisherAlreadyExists(p.name)
    else {
      publishersBySession.addBinding(sessionId, p)
      p
    }
  }

  def updateSuperhero(sessionId: String, s: SuperHero) =
    for {
      _ ← superheroByName(sessionId, s.name)
      _ ← publisherByName(sessionId, s.publisher.name)
      _ ← deleteSuperhero(sessionId, s.name)
      updated ← addSuperhero(sessionId, s)
    } yield updated

  def addSuperhero(sessionId: String, s: SuperHero) =
    publisherByName(sessionId, s.publisher.name).map { _ ⇒
      if (superheroesBySessionUnsafe(sessionId).exists(_.name == s.name)) throw new SuperHeroAlreadyExists(s.name)
      else {
        superheroesBySession.addBinding(sessionId, s)
        s
      }
    }

  def deleteSuperhero(sessionId: String, name: String) =
    superheroByName(sessionId, name).map { sh ⇒
      superheroesBySession.removeBinding(sessionId, sh)
      sh
    }

  def superheroByName(sessionId: String, name: String, protectIdentity: Boolean = false) = Future {
    val sh = {
      if (name == "random") Some(randomSuperhero(sessionId))
      else superheroesBySessionUnsafe(sessionId).find(_.name == name)
    }
    sh.fold(throw new SuperHeroNotFound(name)) { c ⇒
      if (protectIdentity) c.copy(realName = "XXXXX")
      else c
    }
  }

  def allPublishers(session: String) = Future { publishersBySession(session).toSeq }

  def allSuperheroes(session: String) = Future { superheroesBySession(session).toSeq }

  def randomSuperhero(session: String): SuperHero =
    Random.shuffle(superheroesBySession(session).toSeq).head

  private val initialPublishers = Seq(
    Publisher("DC", 1934, "Burbank, California"),
    Publisher("Marvel", 1939, "135 W. 50th Street, New York City")
  )

  private val initialSuperheroes = Seq(
    SuperHero("Batman", "Bruce Wayne", "Gotham city", hasSuperpowers = false, initialPublishers.head),
    SuperHero("Superman", "Clark Kent", "Metropolis", hasSuperpowers = true, initialPublishers.head),
    SuperHero("GreenLantern", "Hal Jordan", "Coast City", hasSuperpowers = true, initialPublishers.head),
    SuperHero("Spiderman", "Peter Parker", "New York", hasSuperpowers = true, initialPublishers.tail.head),
    SuperHero("IronMan", "Tony Stark", "New York", hasSuperpowers = false, initialPublishers.tail.head)
  )
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