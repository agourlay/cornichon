package com.github.agourlay.cornichon.resolver

import java.util.UUID

import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import org.parboiled2._
import org.scalacheck.Gen.Parameters
import org.scalacheck.rng.Seed

import scala.util.{ Failure, Success }

class Resolver(extractors: Map[String, Mapper]) {

  val r = new scala.util.Random()

  def findPlaceholders(input: String): Xor[CornichonError, List[Placeholder]] =
    new PlaceholderParser(input).placeholdersRule.run() match {
      case Failure(e: ParseError) ⇒ right(List.empty)
      case Failure(e: Throwable)  ⇒ left(ResolverParsingError(input, e))
      case Success(dt)            ⇒ right(dt.toList)
    }

  def resolvePlaceholder(ph: Placeholder)(session: Session): Xor[CornichonError, String] =
    builtInPlaceholders.lift(ph.key).map(right).getOrElse {
      val other = ph.key
      (session.getOpt(other, ph.index), extractors.get(other)) match {
        case (Some(v), Some(m))           ⇒ left(AmbiguousKeyDefinition(other))
        case (None, None)                 ⇒ left(KeyNotFoundInSession(other, session))
        case (Some(valueInSession), None) ⇒ right(valueInSession)
        case (None, Some(mapper))         ⇒ applyMapper(mapper, session, ph)
      }
    }

  def builtInPlaceholders: PartialFunction[String, String] = {
    case "random-uuid"             ⇒ UUID.randomUUID().toString
    case "random-positive-integer" ⇒ r.nextInt(10000).toString
    case "random-string"           ⇒ r.nextString(5)
    case "random-boolean"          ⇒ r.nextBoolean().toString
    case "timestamp"               ⇒ (System.currentTimeMillis / 1000).toString
  }

  def applyMapper(m: Mapper, session: Session, ph: Placeholder): Xor[CornichonError, String] = m match {
    case SimpleMapper(gen) ⇒
      Xor.catchNonFatal(gen()).leftMap(SimpleMapperError(ph.fullKey, _))
    case GenMapper(gen) ⇒
      Xor.fromOption(gen.apply(Parameters.default, Seed.random()), GeneratorError(ph.fullKey))
    case TextMapper(key, transform) ⇒
      session.getXor(key, ph.index).map(transform)
    case JsonMapper(key, jsonPath, transform) ⇒
      session.getXor(key, ph.index).flatMap { sessionValue ⇒
        // No placeholders in JsonMapper to avoid accidental infinite recursions.
        JsonPath.run(jsonPath, sessionValue)
          .map(CornichonJson.jsonStringValue)
          .map(transform)
      }
  }

  def fillPlaceholders(input: String)(session: Session) = {
    def loop(placeholders: List[Placeholder], acc: String): Xor[CornichonError, String] =
      placeholders.headOption.fold[Xor[CornichonError, String]](right(acc)) { ph ⇒
        for {
          resolvedValue ← resolvePlaceholder(ph)(session)
          res ← loop(placeholders.tail, acc.replace(ph.fullKey, resolvedValue))
        } yield res
      }

    findPlaceholders(input).flatMap(loop(_, input))
  }

  def fillPlaceholdersUnsafe(input: String)(session: Session): String =
    fillPlaceholders(input)(session).fold(e ⇒ throw e, identity)

  def tuplesResolver(params: Seq[(String, String)], session: Session) = {
    def loop(params: Seq[(String, String)], session: Session, acc: Seq[(String, String)]): Xor[CornichonError, Seq[(String, String)]] =
      params.headOption.fold[Xor[CornichonError, Seq[(String, String)]]](right(acc)) {
        case (name, value) ⇒
          for {
            resolvedName ← fillPlaceholders(name)(session)
            resolvedValue ← fillPlaceholders(value)(session)
            res ← loop(params.tail, session, acc :+ (resolvedName, resolvedValue))
          } yield res
      }

    loop(params, session, Seq.empty[(String, String)])
  }
}

object Resolver {
  def withoutExtractor(): Resolver = new Resolver(Map.empty[String, Mapper])
}

case class ResolverParsingError(input: String, error: Throwable) extends CornichonError {
  val msg = s"error '${error.getMessage}' thrown during placeholder parsing for input $input"
}

case class AmbiguousKeyDefinition(key: String) extends CornichonError {
  val msg = s"ambiguous definition of key '$key' - it is present in both session and extractors"
}

case class SimpleMapperError[A](key: String, e: Throwable) extends CornichonError {
  val msg = s"exception thrown in SimpleMapper '$key' : '${CornichonError.genStacktrace(e)}'"
}

case class GeneratorError(placeholder: String) extends CornichonError {
  val msg = s"generator mapped to placeholder '$placeholder' did not generate a value"
}