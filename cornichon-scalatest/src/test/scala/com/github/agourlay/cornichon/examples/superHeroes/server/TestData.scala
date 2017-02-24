package com.github.agourlay.cornichon.examples.superHeroes.server

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class TestData(implicit executionContext: ExecutionContext) {

  val publishersBySession = new TrieMap[String, Set[Publisher]]
  val superheroesBySession = new TrieMap[String, Set[SuperHero]]

  private def addBinding[A](id: String, a: A, map: TrieMap[String, Set[A]]) =
    map.get(id).fold(map += ((id, Set(a)))) { set ⇒
      // almost atomic...
      map -= id
      map += ((id, set + a))
    }

  private def removeBinding[A](id: String, a: A, map: TrieMap[String, Set[A]]) =
    map.get(id).map { set ⇒
      // almost atomic...
      map -= id
      map += ((id, set - a))
    }

  def createSession(): Future[String] = Future {
    val newSessionId = Random.alphanumeric.take(8).mkString
    initialPublishers.foreach(addBinding(newSessionId, _, publishersBySession))
    initialSuperheroes.foreach(addBinding(newSessionId, _, superheroesBySession))
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
      addBinding(sessionId, p, publishersBySession)
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
        addBinding(sessionId, s, superheroesBySession)
        s
      }
    }

  def deleteSuperhero(sessionId: String, name: String) =
    superheroByName(sessionId, name).map { sh ⇒
      removeBinding(sessionId, sh, superheroesBySession)
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