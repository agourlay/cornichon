package com.github.agourlay.cornichon.resolver

import java.util.UUID

import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }

import scala.collection.concurrent.TrieMap

class Resolver(extractors: Map[String, Mapper]) {

  val r = new scala.util.Random()

  // When steps are nested (repeat, eventually, retryMax) it is wasteful to repeat the parsing process of looking for placeholders.
  // There is one resolver per Feature so the cache is not living too long.
  private val placeholdersCache = TrieMap.empty[String, Either[CornichonError, List[Placeholder]]]

  def findPlaceholders(input: String): Either[CornichonError, List[Placeholder]] =
    placeholdersCache.getOrElseUpdate(input, PlaceholderParser.parse(input))

  def resolvePlaceholder(ph: Placeholder)(session: Session): Either[CornichonError, String] =
    builtInPlaceholders.lift(ph.key).map(Right(_)).getOrElse {
      val otherKeyName = ph.key
      val otherKeyIndice = ph.index
      (session.getOpt(otherKeyName, otherKeyIndice), extractors.get(otherKeyName)) match {
        case (Some(_), Some(_))           ⇒ Left(AmbiguousKeyDefinition(otherKeyName))
        case (None, None)                 ⇒ Left(KeyNotFoundInSession(otherKeyName, otherKeyIndice, session))
        case (Some(valueInSession), None) ⇒ Right(valueInSession)
        case (None, Some(mapper))         ⇒ applyMapper(mapper, session, ph)
      }
    }

  def builtInPlaceholders: PartialFunction[String, String] = {
    case "random-uuid"             ⇒ UUID.randomUUID().toString
    case "random-positive-integer" ⇒ r.nextInt(10000).toString
    case "random-string"           ⇒ r.nextString(5)
    case "random-alphanum-string"  ⇒ r.alphanumeric.take(5).mkString("")
    case "random-boolean"          ⇒ r.nextBoolean().toString
    case "random-timestamp"        ⇒ (Math.abs(System.currentTimeMillis - r.nextLong()) / 1000).toString
    case "current-timestamp"       ⇒ (System.currentTimeMillis / 1000).toString
  }

  def applyMapper(m: Mapper, session: Session, ph: Placeholder): Either[CornichonError, String] = m match {
    case SimpleMapper(gen) ⇒
      Either.catchNonFatal(gen()).leftMap(SimpleMapperError(ph.fullKey, _))
    case TextMapper(key, transform) ⇒
      session.get(key, ph.index).map(transform)
    case JsonMapper(key, jsonPath, transform) ⇒
      session.get(key, ph.index).flatMap { sessionValue ⇒
        // No placeholders in JsonMapper to avoid accidental infinite recursions.
        JsonPath.run(jsonPath, sessionValue)
          .map(CornichonJson.jsonStringValue)
          .map(transform)
      }
  }

  def fillPlaceholders[A: Resolvable](input: A)(session: Session): Either[CornichonError, A] = {
    val ri = Resolvable[A]
    val resolvableForm = ri.toResolvableForm(input)
    fillPlaceholders(resolvableForm)(session).map { resolved ⇒
      // If the input did not contain placeholders,
      // we can return the original value directly
      // and avoid an extra transformation from the resolved form
      if (resolved == resolvableForm) input else ri.fromResolvableForm(resolved)
    }
  }

  def fillPlaceholders(input: String)(session: Session) = {
    def loop(placeholders: List[Placeholder], acc: String): Either[CornichonError, String] =
      if (placeholders.isEmpty) Right(acc)
      else {
        val ph = placeholders.head
        for {
          resolvedValue ← resolvePlaceholder(ph)(session)
          res ← loop(placeholders.tail, acc.replace(ph.fullKey, resolvedValue))
        } yield res
      }

    findPlaceholders(input).flatMap(loop(_, input))
  }

  def fillPlaceholdersUnsafe(input: String)(session: Session): String =
    fillPlaceholders(input)(session).fold(ce ⇒ throw ce.toException, identity)

  def fillPlaceholders(params: Seq[(String, String)])(session: Session): Either[CornichonError, Seq[(String, String)]] = {
    def loop(params: Seq[(String, String)], session: Session, acc: Seq[(String, String)]): Either[CornichonError, Seq[(String, String)]] =
      if (params.isEmpty) Right(acc.reverse)
      else {
        val (name, value) = params.head
        for {
          resolvedName ← fillPlaceholders(name)(session)
          resolvedValue ← fillPlaceholders(value)(session)
          res ← loop(params.tail, session, (resolvedName, resolvedValue) +: acc)
        } yield res
      }

    loop(params, session, Nil)
  }
}

object Resolver {
  def withoutExtractor(): Resolver = new Resolver(Map.empty[String, Mapper])
}

case class AmbiguousKeyDefinition(key: String) extends CornichonError {
  val baseErrorMessage = s"ambiguous definition of key '$key' - it is present in both session and extractors"
}

case class SimpleMapperError[A](key: String, e: Throwable) extends CornichonError {
  val baseErrorMessage = s"exception thrown in SimpleMapper '$key' :\n'${CornichonError.genStacktrace(e)}'"
}