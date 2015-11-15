package com.github.agourlay.cornichon.core

import java.util.UUID

import cats.data.Xor
import cats.data.Xor.{ left, right }
import org.parboiled2._

import scala.util._

class Resolver(extractors: Map[String, Mapper]) {

  def findPlaceholders(input: String): List[Placeholder] = {
    val p = new PlaceholderParser(input)
    p.placeholdersRule.run() match {
      case Failure(e: ParseError) ⇒
        println(p.formatError(e, new ErrorFormatter(showTraces = true)))
        throw new ResolverParseError(p.formatError(e, new ErrorFormatter(showTraces = true)))
      case Failure(e: Throwable) ⇒
        throw new ResolverParsingError(e)
      case Success(dt) ⇒ dt.toList
    }
  }

  def resolvePlaceholder(ph: Placeholder)(session: Session): Xor[ResolverError, String] = ph.key match {
    case "random-uuid"             ⇒ right(UUID.randomUUID().toString)
    case "random-positive-integer" ⇒ right(scala.util.Random.nextInt(100).toString)
    case "random-string"           ⇒ right(scala.util.Random.nextString(5))
    case "random-boolean"          ⇒ right(scala.util.Random.nextBoolean().toString)
    case "timestamp"               ⇒ right((System.currentTimeMillis / 1000).toString)
    case other: String ⇒
      extractors.get(other).fold[Xor[ResolverError, String]] {
        session.getOpt(other, ph.index).map(right).getOrElse(left(SimpleResolverError(other, session)))
      } { mapper ⇒
        Try {
          session.getOpt(mapper.key, ph.index).map(mapper.transform)
        } match {
          case Success(value) ⇒ value.map(right).getOrElse(left(SimpleResolverError(other, session)))
          case Failure(e) ⇒
            left(ExtractorResolverError(other, session, e))
        }
      }
  }

  // TODO should accumulate errors
  def fillPlaceholders(input: String)(session: Session): Xor[ResolverError, String] = {
    def loop(placeholders: List[Placeholder], acc: String): Xor[ResolverError, String] = {
      placeholders.headOption.fold[Xor[ResolverError, String]](right(acc)) { ph ⇒
        for {
          resolvedValue ← resolvePlaceholder(ph)(session)
          res ← loop(placeholders.tail, acc.replace(ph.fullKey, resolvedValue))
        } yield res
      }
    }
    loop(findPlaceholders(input), input)
  }

  def fillPlaceholdersUnsafe(input: String)(session: Session): String =
    fillPlaceholders(input)(session).fold(e ⇒ throw e, identity)

  // TODO accumulate errors
  def tuplesResolver(params: Seq[(String, String)], session: Session): Xor[ResolverError, Seq[(String, String)]] = {
    def loop(params: Seq[(String, String)], session: Session, acc: Seq[(String, String)]): Xor[ResolverError, Seq[(String, String)]] = {
      params.headOption.fold[Xor[ResolverError, Seq[(String, String)]]](right(acc)) {
        case (name, value) ⇒
          for {
            resolvedName ← fillPlaceholders(name)(session)
            resolvedValue ← fillPlaceholders(value)(session)
            res ← loop(params.tail, session, acc :+ (resolvedName, resolvedValue))
          } yield res
      }
    }
    loop(params, session, Seq.empty[(String, String)])
  }
}

object Resolver {
  def withoutExtractor(): Resolver = new Resolver(Map.empty[String, Mapper])
}

class PlaceholderParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    Ignore ~ zeroOrMore(PlaceholderRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  def PlaceholderRule = rule('<' ~ PlaceholderTXT ~ optIndex ~ '>' ~> Placeholder)

  val notAllowedInKey = "\r\n<>[] "

  def optIndex = rule(optional('[' ~ Number ~ ']'))

  def PlaceholderTXT = rule(capture(oneOrMore(CharPredicate.Visible -- notAllowedInKey)))

  def Ignore = rule { zeroOrMore(!'<' ~ ANY) }

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

case class Placeholder(key: String, index: Option[Int]) {
  val fullKey = index.fold(s"<$key>") { index ⇒
    s"<$key[$index]>"
  }
}

case class Mapper(key: String, transform: String ⇒ String)