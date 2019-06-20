package com.github.agourlay.cornichon.core

import cats.Show
import cats.syntax.show._
import cats.syntax.monoid._
import cats.instances.string._
import cats.instances.map._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import cats.kernel.Monoid
import com.github.agourlay.cornichon.core.Session._
import com.github.agourlay.cornichon.util.{ Caching, Strings }

import scala.collection.immutable.{ HashMap, StringOps }
// TODO try replacing Vector by ArraySeq in Scala 2.13
// https://www.scala-lang.org/api/2.13.0/scala/collection/immutable/ArraySeq.html
case class Session(content: Map[String, Vector[String]]) extends AnyVal {

  //Specialised Option version to avoid Either.left creation through Either.toOption
  def getOpt(key: String, stackingIndice: Option[Int] = None): Option[String] =
    content.get(key).flatMap { values ⇒
      val indice = stackingIndice.getOrElse(values.size - 1)
      if (values.size < indice)
        None
      else
        Some(values(indice))
    }

  def get(key: String, stackingIndice: Option[Int] = None): Either[CornichonError, String] =
    content.get(key) match {
      case None ⇒
        KeyNotFoundInSession(key, this).asLeft
      case Some(values) ⇒
        val indice = stackingIndice.getOrElse(values.size - 1)
        if (values.size < indice)
          IndiceNotFoundForKey(key, indice, values).asLeft
        else
          values(indice).asRight
    }

  def get(sessionKey: SessionKey): Either[CornichonError, String] =
    get(sessionKey.name, sessionKey.index)

  def getUnsafe(key: String, stackingIndice: Option[Int] = None): String =
    get(key, stackingIndice).valueUnsafe

  def getList(keys: Seq[String]): Either[CornichonError, List[String]] =
    keys.toList.traverse(get(_))

  def getHistory(key: String): Either[KeyNotFoundInSession, Vector[String]] =
    content.get(key).toRight(KeyNotFoundInSession(key, this))

  def getPrevious(key: String): Either[CornichonError, Option[String]] =
    for {
      values ← content.get(key).toRight(KeyNotFoundInSession(key, this))
      indice = values.size - 2
      value ← values.lift(indice).asRight
    } yield value

  // Not returning the same key wrapped to avoid allocations
  private def validateKey(key: String): Either[CornichonError, Done] =
    knownKeysCache.get(key, key ⇒ {
      val trimmedKey = new StringOps(key.trim)
      if (trimmedKey.isEmpty)
        EmptyKey.asLeft
      else if (Session.notAllowedInKey.exists(forbidden ⇒ trimmedKey.contains(forbidden)))
        IllegalKey(key).asLeft
      else
        Done.rightDone
    })

  private def updateContent(c1: Map[String, Vector[String]])(key: String, value: String): Map[String, Vector[String]] =
    c1.get(key) match {
      case None         ⇒ c1 + (key → Vector(value))
      case Some(values) ⇒ (c1 - key) + (key → values.:+(value))
    }

  def addValue(key: String, value: String): Either[CornichonError, Session] =
    validateKey(key).map(_ ⇒ Session(updateContent(content)(key, value)))

  def addValueUnsafe(key: String, value: String): Session =
    addValue(key, value).valueUnsafe

  def addValues(tuples: (String, String)*): Either[CornichonError, Session] =
    tuples match {
      case t :: Nil ⇒
        addValue(t._1, t._2)
      case _ ⇒
        tuples
          .toList
          .traverse(t ⇒ validateKey(t._1)) //validate all keys and then work at the Map level for efficiency
          .map { _ ⇒
            // Idea: this could be a mutable map for the time of the updates
            val updatedContent = tuples.foldLeft(this.content)((c, t) ⇒ updateContent(c)(t._1, t._2))
            Session(updatedContent)
          }
    }

  def addValuesUnsafe(tuples: (String, String)*): Session =
    addValues(tuples: _*).valueUnsafe

  def removeKey(key: String): Session =
    Session(content - key)

  def rollbackKey(key: String): Either[KeyNotFoundInSession, Session] =
    getHistory(key).map { values ⇒
      val s = Session(content - key)
      val previous = values.init
      if (previous.isEmpty)
        s
      else
        s.copy(content = s.content + (key -> previous))
    }

}

object Session {
  val newEmpty = Session(HashMap.empty)

  // Saved as StringOps as it uses '.exists' extremely often
  val notAllowedInKey: StringOps = new StringOps("\r\n<>/[] ")

  private val knownKeysCache = Caching.buildCache[String, Either[CornichonError, Done]]()

  implicit val monoidSession: Monoid[Session] = new Monoid[Session] {
    def empty: Session = newEmpty
    def combine(x: Session, y: Session): Session =
      Session(x.content.combine(y.content))
  }

  implicit val showSession: Show[Session] = Show.show[Session] { s ⇒
    if (s.content.isEmpty)
      "empty"
    else
      s.content.toSeq
        .sortBy(_._1)
        .map(pair ⇒ pair._1 + " -> " + pair._2.toIterator.map(_.show).mkString("Values(", ", ", ")"))
        .mkString("\n")
  }
}

case class SessionKey(name: String, index: Option[Int] = None) {
  def atIndex(index: Int) = copy(index = Some(index))
}

object SessionKey {
  implicit val showSessionKey = Show.show[SessionKey] { sk ⇒
    val key = sk.name
    val indice = sk.index
    s"$key${indice.map(i ⇒ s"[$i]").getOrElse("")}"
  }
}

case class KeyNotFoundInSession(key: String, s: Session) extends CornichonError {
  lazy val similarKeysMsg = {
    val similar = s.content.keys.filter(Strings.levenshtein(_, key) == 1)
    if (similar.isEmpty)
      ""
    else
      s"maybe you meant ${similar.map(s ⇒ s"'$s'").mkString(" or ")}"
  }
  lazy val baseErrorMessage = s"key '$key' can not be found in session $similarKeysMsg\n${s.show}"
}

case object EmptyKey extends CornichonError {
  lazy val baseErrorMessage = "key can not be empty"
}

case class IllegalKey(key: String) extends CornichonError {
  lazy val baseErrorMessage = s"Illegal session key '$key'\nsession key can not contain the following chars ${Session.notAllowedInKey.mkString(" ")}"
}

case class IndiceNotFoundForKey(key: String, indice: Int, values: Vector[String]) extends CornichonError {
  lazy val baseErrorMessage = s"indice '$indice' not found for key '$key' with values \n" +
    s"${values.zipWithIndex.map { case (v, i) ⇒ s"$i -> $v" }.mkString("\n")}"
}