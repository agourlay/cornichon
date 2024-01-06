package com.github.agourlay.cornichon.core

import cats.Show
import cats.syntax.show._
import cats.syntax.monoid._

import cats.kernel.Monoid
import com.github.agourlay.cornichon.core.Session._
import com.github.agourlay.cornichon.util.StringUtils
import com.github.agourlay.cornichon.util.TraverseUtils.traverseIL
import scala.collection.immutable.HashMap

// TODO try replacing Vector by ArraySeq in Scala 2.13
// https://www.scala-lang.org/api/2.13.0/scala/collection/immutable/ArraySeq.html
case class Session(content: Map[String, Vector[String]]) extends AnyVal {

  //Specialised Option version to avoid Either.left creation through Either.toOption
  def getOpt(key: String, stackingIndex: Option[Int] = None): Option[String] =
    content.get(key).flatMap { values =>
      val index = stackingIndex.getOrElse(values.size - 1)
      if (values.size < index)
        None
      else
        Some(values(index))
    }

  def get(key: String, stackingIndex: Option[Int] = None): Either[CornichonError, String] =
    content.get(key) match {
      case None =>
        Left(KeyNotFoundInSession(key, this))
      case Some(values) =>
        val valueCount = values.length
        val index = stackingIndex.getOrElse(valueCount - 1)
        if (valueCount < index)
          Left(IndexNotFoundForKey(key, index, values))
        else
          Right(values(index))
    }

  def get(sessionKey: SessionKey): Either[CornichonError, String] =
    get(sessionKey.name, sessionKey.index)

  def getUnsafe(key: String, stackingIndex: Option[Int] = None): String =
    get(key, stackingIndex).valueUnsafe

  def getList(keys: Seq[String]): Either[CornichonError, List[String]] =
    traverseIL(keys.iterator)(get(_))

  def getHistory(key: String): Either[KeyNotFoundInSession, Vector[String]] =
    content.get(key).toRight(KeyNotFoundInSession(key, this))

  def getPrevious(key: String): Either[CornichonError, Option[String]] =
    getHistory(key).map { values =>
      val index = values.size - 2
      values.lift(index)
    }

  def getMandatoryPrevious(key: String): Either[CornichonError, String] = {
    getPrevious(key).flatMap {
      case None        => Left(KeyWithoutPreviousValue(key, this))
      case Some(value) => Right(value)
    }
  }

  private def updateContent(c1: Map[String, Vector[String]])(key: String, value: String): Map[String, Vector[String]] =
    c1.get(key) match {
      case None         => c1.updated(key, Vector(value))
      case Some(values) => c1.updated(key, values :+ value)
    }

  // No need for key validation when used with built-in keys
  protected[cornichon] def addValueInternal(key: String, value: String): Session =
    Session(updateContent(content)(key, value))

  // Validate the key adding it to the session
  def addValue(key: String, value: String): Either[CornichonError, Session] =
    if (key.isBlank)
      Left(EmptyKey)
    else if (Session.notAllowedInKey.exists(forbidden => key.contains(forbidden)))
      Left(IllegalKey(key))
    else
      Right(Session(updateContent(content)(key, value)))

  def addValueUnsafe(key: String, value: String): Session =
    addValue(key, value).valueUnsafe

  def addValues(tuples: (String, String)*): Either[CornichonError, Session] = {
    var resultSession = this
    for ((k, v) <- tuples) {
      resultSession.addValue(k, v) match {
        case e @ Left(_)       => return e
        case Right(newSession) => resultSession = newSession
      }
    }
    Right(resultSession)
  }

  def addValuesUnsafe(tuples: (String, String)*): Session =
    addValues(tuples: _*).valueUnsafe

  def removeKey(key: String): Session =
    Session(content - key)

  def rollbackKey(key: String): Either[KeyNotFoundInSession, Session] =
    getHistory(key).map { values =>
      val s = Session(content - key)
      val previous = values.init
      if (previous.isEmpty)
        s
      else
        s.copy(content = s.content.updated(key, previous))
    }

}

object Session {
  val newEmpty: Session = Session(HashMap.empty)

  val notAllowedInKey: String = "\r\n<>/ []"

  implicit val monoidSession: Monoid[Session] = new Monoid[Session] {
    def empty: Session = newEmpty
    def combine(x: Session, y: Session): Session =
      Session(x.content.combine(y.content))
  }

  implicit val showSession: Show[Session] = Show.show[Session] { s =>
    val keyCount = s.content.size
    if (keyCount == 0)
      "empty"
    else {
      // custom unrolled nested mkString for performance
      val keyCount = s.content.size
      val averageLen = s.content.valuesIterator.map(_.size).sum / keyCount
      // best effort sizing
      val builder = new StringBuilder(keyCount * averageLen * 32)
      val sortedPairs = s.content.toSeq.sortBy(_._1)
      var i = 0
      for ((key, values) <- sortedPairs) {
        builder.append(key)
        builder.append(" -> ")
        // inner values
        builder.append("Values(")
        var j = 0
        for (v <- values) {
          builder.append(v)
          if (j < values.size - 1) {
            builder.append(", ")
          }
          j += 1
        }
        builder.append(")")
        if (i < keyCount - 1) {
          builder.append('\n')
        }
        i += 1
      }
      builder.toString()
    }
  }
}

case class SessionKey(name: String, index: Option[Int] = None) {
  def atIndex(index: Int): SessionKey = copy(index = Some(index))
}

object SessionKey {
  implicit val showSessionKey: Show[SessionKey] = Show.show[SessionKey] { sk =>
    val key = sk.name
    val index = sk.index
    s"$key${index.map(i => s"[$i]").getOrElse("")}"
  }
}

case class KeyNotFoundInSession(key: String, s: Session) extends CornichonError {
  private lazy val similarKeysMsg = {
    val similar = s.content.keysIterator.filter(StringUtils.levenshtein(_, key) == 1).toSeq.sorted
    if (similar.isEmpty)
      ""
    else
      s" maybe you meant ${similar.iterator.map(s => s"'$s'").mkString(" or ")}"
  }
  lazy val baseErrorMessage = s"key '$key' can not be found in session$similarKeysMsg\n${s.show}"
}

case object EmptyKey extends CornichonError {
  lazy val baseErrorMessage = "key can not be empty"
}

case class IllegalKey(key: String) extends CornichonError {
  lazy val baseErrorMessage = s"Illegal session key '$key'\nsession key can not contain the following chars ${Session.notAllowedInKey.mkString(" ")}"
}

case class IndexNotFoundForKey(key: String, index: Int, values: Vector[String]) extends CornichonError {
  lazy val baseErrorMessage = s"index '$index' not found for key '$key' with values \n" +
    values.iterator.zipWithIndex.map { case (v, i) => s"$i -> $v" }.mkString("\n")
}

case class KeyWithoutPreviousValue(key: String, s: Session) extends CornichonError {
  lazy val baseErrorMessage = s"key '$key' does not have previous value in session\n${s.show}"
}