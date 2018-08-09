package com.github.agourlay.cornichon.resolver

import java.util.UUID

import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import com.github.agourlay.cornichon.resolver.PlaceholderResolver._
import com.github.agourlay.cornichon.util.Caching

class PlaceholderResolver(extractors: Map[String, Mapper]) {

  // FIXME - should be globally seeded to reproduce tests
  val r = new scala.util.Random()

  // When steps are nested (repeat, eventually, retryMax) it is wasteful to repeat the parsing process of looking for placeholders.
  // There is one resolver per Feature so the cache is not living too long.
  private val placeholdersCache = Caching.buildCache[String, Either[CornichonError, List[Placeholder]]]()

  def findPlaceholders(input: String): Either[CornichonError, List[Placeholder]] =
    placeholdersCache.get(input, k ⇒ PlaceholderParser.parse(k))

  def resolvePlaceholder(ph: Placeholder)(session: Session): Either[CornichonError, String] =
    builtInPlaceholders.lift(ph.key).map(Right(_)).getOrElse {
      val otherKeyName = ph.key
      val otherKeyIndice = ph.index
      (session.get(otherKeyName, otherKeyIndice), extractors.get(otherKeyName)) match {
        case (v, None)               ⇒ v
        case (Left(_), Some(mapper)) ⇒ applyMapper(otherKeyName, mapper, session, ph)
        case (Right(_), Some(_))     ⇒ Left(AmbiguousKeyDefinition(otherKeyName))
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

  def applyMapper(bindingKey: String, m: Mapper, session: Session, ph: Placeholder): Either[CornichonError, String] = m match {
    case SimpleMapper(gen) ⇒
      Either.catchNonFatal(gen()).leftMap(SimpleMapperError(ph.fullKey, _))
    case SessionMapper(gen) ⇒
      gen(session).leftMap(SessionMapperError(ph.fullKey, _))
    case RandomMapper(gen) ⇒
      Either.catchNonFatal(gen(r)).leftMap(RandomMapperError(ph.fullKey, _))
    case HistoryMapper(key, transform) ⇒
      session.getHistory(key)
        .leftMap { o: CornichonError ⇒ MapperKeyNotFoundInSession(bindingKey, o) }
        .map(transform)
    case TextMapper(key, transform) ⇒
      session.get(key, ph.index)
        .leftMap { o: CornichonError ⇒ MapperKeyNotFoundInSession(bindingKey, o) }
        .map(transform)
    case JsonMapper(key, jsonPath, transform) ⇒
      session.get(key, ph.index)
        .leftMap { o: CornichonError ⇒ MapperKeyNotFoundInSession(bindingKey, o) }
        .flatMap { sessionValue ⇒
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

  def fillPlaceholders(input: String)(session: Session): Either[CornichonError, String] =
    findPlaceholders(input).flatMap {
      _.foldLeft[Either[CornichonError, String]](Right(input)) { (accE, ph) ⇒
        for {
          acc ← accE
          resolvedValue ← resolvePlaceholder(ph)(session)
        } yield acc.replace(ph.fullKey, resolvedValue)
      }
    }

  def fillPlaceholders(params: Seq[(String, String)])(session: Session): Either[CornichonError, List[(String, String)]] =
    params.foldRight[Either[CornichonError, List[(String, String)]]](rightNil) { (p, accE) ⇒
      val (name, value) = p
      for {
        acc ← accE
        resolvedName ← fillPlaceholders(name)(session)
        resolvedValue ← fillPlaceholders(value)(session)
      } yield (resolvedName, resolvedValue) :: acc // foldRight + prepend
    }

}

object PlaceholderResolver {
  def withoutExtractor(): PlaceholderResolver = new PlaceholderResolver(Map.empty[String, Mapper])
  private val rightNil = Right(Nil)
}

case class AmbiguousKeyDefinition(key: String) extends CornichonError {
  lazy val baseErrorMessage = s"ambiguous definition of key '$key' - it is present in both session and extractors"
}

case class MapperKeyNotFoundInSession(key: String, underlyingError: CornichonError) extends CornichonError {
  lazy val baseErrorMessage = s"Error occurred while running Mapper attached to key '$key'"
  override val causedBy = underlyingError :: Nil
}

case class RandomMapperError[A](key: String, e: Throwable) extends CornichonError {
  lazy val baseErrorMessage = s"exception thrown in RandomMapper '$key' :\n'${CornichonError.genStacktrace(e)}'"
}

case class SimpleMapperError[A](key: String, e: Throwable) extends CornichonError {
  lazy val baseErrorMessage = s"exception thrown in SimpleMapper '$key' :\n'${CornichonError.genStacktrace(e)}'"
}

case class SessionMapperError[A](key: String, underlyingError: CornichonError) extends CornichonError {
  lazy val baseErrorMessage = s"Error thrown in SessionMapper '$key')'"
  override val causedBy = underlyingError :: Nil
}