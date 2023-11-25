package com.github.agourlay.cornichon.resolver

import java.util.concurrent.atomic.AtomicLong
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import com.github.agourlay.cornichon.resolver.PlaceholderGenerator._
import com.github.agourlay.cornichon.util.StringUtils

import scala.collection.mutable.ListBuffer

object PlaceholderResolver {

  private val rightNil = Right(Nil)
  private val globalAtomicLong = new AtomicLong(1L) // can create non deterministic runs

  def globalNextLong(): Long = globalAtomicLong.getAndIncrement()

  val builtInPlaceholderGenerators: List[PlaceholderGenerator] =
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
    builtInPlaceholderGenerators.groupBy(_.key).map { case (k, values) => (k, values.head) } // we know it is not empty

  def findPlaceholders(input: String): Either[CornichonError, Vector[Placeholder]] =
    PlaceholderParser.parse(input)

  private def resolvePlaceholder(ph: Placeholder)(session: Session, rc: RandomContext, customExtractors: Map[String, Mapper], sessionOnlyMode: Boolean): Either[CornichonError, String] =
    placeholderGeneratorsByLabel.get(ph.key) match {
      case Some(pg) =>
        // in session mode we leave the generators untouched to avoid side effects
        val v = if (sessionOnlyMode) ph.fullKey else pg.gen(rc)
        Right(v)
      case None =>
        val otherKeyName = ph.key
        val otherKeyIndex = ph.index
        (session.get(otherKeyName, otherKeyIndex), customExtractors.get(otherKeyName)) match {
          case (v, None)               => v
          case (Left(_), Some(mapper)) => applyMapper(otherKeyName, mapper, ph)(session, rc)
          case (Right(_), Some(_))     => Left(AmbiguousKeyDefinition(otherKeyName))
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
    findPlaceholders(input).flatMap { placeholders =>
      val len = placeholders.length
      if (len == 0)
        Right(input)
      else {
        var i = 0
        val patterns = Vector.newBuilder[(String, String)]
        while (i < len) {
          val ph = placeholders(i)
          val resolvedValue = resolvePlaceholder(ph)(session, rc, customExtractors, sessionOnlyMode)
          resolvedValue match {
            case Right(resolved) => patterns += ((ph.fullKey, resolved))
            case Left(err)       => return Left(err)
          }
          i += 1
        }
        val patternsResult = patterns.result()
        val updatedInput = StringUtils.replace_patterns_in_order(input, patternsResult)
        Right(updatedInput)
      }
    }

  def fillPlaceholdersPairs(pairs: Seq[(String, String)])(session: Session, randomContext: RandomContext, customExtractors: Map[String, Mapper]): Either[CornichonError, List[(String, String)]] = {
    if (pairs.isEmpty)
      rightNil
    else {
      val acc = new ListBuffer[(String, String)]()
      for ((name, value) <- pairs) {
        val res = for {
          resolvedName <- fillPlaceholders(name)(session, randomContext, customExtractors)
          resolvedValue <- fillPlaceholders(value)(session, randomContext, customExtractors)
        } yield (resolvedName, resolvedValue)
        res match {
          case Right(tuple) => acc += tuple
          case Left(err)    => return Left(err)
        }
      }
      Right(acc.toList)
    }
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
            .map(json => transform(CornichonJson.jsonStringValue(json)))
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