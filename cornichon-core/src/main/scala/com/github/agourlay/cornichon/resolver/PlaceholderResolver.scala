package com.github.agourlay.cornichon.resolver

import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Matcher

import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import com.github.agourlay.cornichon.resolver.PlaceholderGenerator._
import com.github.agourlay.cornichon.util.Caching

object PlaceholderResolver {

  private val rightNil = Nil.asRight
  private val placeholdersCache = Caching.buildCache[String, Either[CornichonError, List[Placeholder]]]()
  private val globalAtomicLong = new AtomicLong(1L) // can create non deterministic runs

  def globalNextLong(): Long = globalAtomicLong.getAndIncrement()

  private val builtInPlaceholderGenerators: List[PlaceholderGenerator] =
    randomUUID ::
      randomPositiveInteger ::
      randomString ::
      randomAlphanumString ::
      randomBoolean ::
      scenarioUniqueNumber ::
      globalUniqueNumber ::
      randomTimestamp ::
      currentTimestamp ::
      Nil

  private val placeholderGeneratorsByLabel: Map[String, PlaceholderGenerator] =
    builtInPlaceholderGenerators.groupBy(_.key).map { case (k, h :: _) => (k, h) }

  def findPlaceholders(input: String): Either[CornichonError, List[Placeholder]] =
    placeholdersCache.get(input, k => PlaceholderParser.parse(k))

  private def resolvePlaceholder(ph: Placeholder)(session: Session, rc: RandomContext, customExtractors: Map[String, Mapper], sessionOnlyMode: Boolean): Either[CornichonError, String] =
    placeholderGeneratorsByLabel.get(ph.key)
      .map(pg => if (sessionOnlyMode) pg.key else pg.gen(rc)) // in session mode we leave the generators untouched to avoid side effects
      .map(Right.apply)
      .getOrElse {
        val otherKeyName = ph.key
        val otherKeyIndex = ph.index
        (session.get(otherKeyName, otherKeyIndex), customExtractors.get(otherKeyName)) match {
          case (v, None)               => v
          case (Left(_), Some(mapper)) => applyMapper(otherKeyName, mapper, ph)(session, rc)
          case (Right(_), Some(_))     => AmbiguousKeyDefinition(otherKeyName).asLeft
        }
      }

  def fillPlaceholdersResolvable[A: Resolvable](resolvableInput: A)(session: Session, randomContext: RandomContext, customExtractors: Map[String, Mapper]): Either[CornichonError, A] = {
    val ri = Resolvable[A]
    val resolvableForm = ri.toResolvableForm(resolvableInput)
    fillPlaceholders(resolvableForm)(session, randomContext, customExtractors).map { resolved =>
      // If the input did not contain placeholders,
      // we can return the original value directly
      // and avoid an extra transformation from the resolved form
      if (resolved == resolvableForm) resolvableInput else ri.fromResolvableForm(resolved)
    }
  }

  def fillPlaceholders(input: String)(session: Session, rc: RandomContext, customExtractors: Map[String, Mapper], sessionOnlyMode: Boolean = false): Either[CornichonError, String] =
    findPlaceholders(input).flatMap {
      _.foldLeft(input.asRight[CornichonError]) { (accE, ph) =>
        for {
          acc <- accE
          resolvedValue <- resolvePlaceholder(ph)(session, rc, customExtractors, sessionOnlyMode)
        } yield ph.pattern.matcher(acc).replaceAll(Matcher.quoteReplacement(resolvedValue))
      }
    }

  def fillPlaceholdersMany(params: Seq[(String, String)])(session: Session, randomContext: RandomContext, customExtractors: Map[String, Mapper]): Either[CornichonError, List[(String, String)]] =
    params.foldRight[Either[CornichonError, List[(String, String)]]](rightNil) {
      case ((name, value), accE) =>
        for {
          acc <- accE
          resolvedName <- fillPlaceholders(name)(session, randomContext, customExtractors)
          resolvedValue <- fillPlaceholders(value)(session, randomContext, customExtractors)
        } yield (resolvedName, resolvedValue) :: acc // foldRight + prepend
    }

  private def applyMapper(bindingKey: String, m: Mapper, ph: Placeholder)(session: Session, randomContext: RandomContext): Either[CornichonError, String] = m match {
    case SimpleMapper(gen) =>
      Either.catchNonFatal(gen()).leftMap(SimpleMapperError(ph.fullKey, _))
    case SessionMapper(gen) =>
      gen(session).leftMap(SessionMapperError(ph.fullKey, _))
    case RandomMapper(gen) =>
      Either.catchNonFatal(gen(randomContext)).leftMap(RandomMapperError(ph.fullKey, _))
    case HistoryMapper(key, transform) =>
      session.getHistory(key)
        .leftMap { o: CornichonError => MapperKeyNotFoundInSession(bindingKey, o) }
        .map(transform)
    case TextMapper(key, transform) =>
      session.get(key, ph.index)
        .leftMap { o: CornichonError => MapperKeyNotFoundInSession(bindingKey, o) }
        .map(transform)
    case JsonMapper(key, jsonPath, transform) =>
      session.get(key, ph.index)
        .leftMap { o: CornichonError => MapperKeyNotFoundInSession(bindingKey, o) }
        .flatMap { sessionValue =>
          // No placeholders in JsonMapper to avoid accidental infinite recursions.
          JsonPath.runStrict(jsonPath, sessionValue)
            .map(CornichonJson.jsonStringValue)
            .map(transform)
        }
  }
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