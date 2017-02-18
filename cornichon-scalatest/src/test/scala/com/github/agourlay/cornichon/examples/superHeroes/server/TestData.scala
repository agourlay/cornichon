package com.github.agourlay.cornichon.examples.superHeroes.server

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class TestData(implicit executionContext: ExecutionContext) {

  val publishersBySession = new mutable.HashMap[String, mutable.Set[Publisher]] with mutable.MultiMap[String, Publisher]
  val superheroesBySession = new mutable.HashMap[String, mutable.Set[SuperHero]] with mutable.MultiMap[String, SuperHero]

  def createSession(): Future[String] = Future {
    val newSessionId = Random.alphanumeric.take(8).mkString
    initialPublishers.foreach(publishersBySession.addBinding(newSessionId, _))
    initialSuperheroes.foreach(superheroesBySession.addBinding(newSessionId, _))
    newSessionId
  }

  def publishersBySessionUnsafe(sessionId: String) = publishersBySession.getOrElse(sessionId, throw SessionNotFound(sessionId))

  def superheroesBySessionUnsafe(sessionId: String) = superheroesBySession.getOrElse(sessionId, throw SessionNotFound(sessionId))

  def publisherByName(sessionId: String, name: String) = Future {
    publishersBySessionUnsafe(sessionId).find(_.name == name).fold(throw PublisherNotFound(name)) { c ⇒ c }
  }

  def addPublisher(sessionId: String, p: Publisher) = Future {
    if (publishersBySessionUnsafe(sessionId).exists(_.name == p.name)) throw PublisherAlreadyExists(p.name)
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
      if (superheroesBySessionUnsafe(sessionId).exists(_.name == s.name)) throw SuperHeroAlreadyExists(s.name)
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
    sh.fold(throw SuperHeroNotFound(name)) { c ⇒
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