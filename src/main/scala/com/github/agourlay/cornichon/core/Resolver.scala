package com.github.agourlay.cornichon.core

import java.util.UUID

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.annotation.tailrec
import scala.util._

class Resolver(extractors: Map[String, Mapper]) {

  // Migrate to PB2 if too many errors
  private def findPlaceholders(input: String): List[String] = {
    @tailrec
    def loop(input: String, acc: List[String]): List[String] =
      if (input.isEmpty) acc
      else if (placeholderStarts(input)) {
        val placeHolder = input.takeWhile(c ⇒ c != '>') + ">"
        loop(input.drop(placeHolder.length), acc.::(placeHolder))
      } else if (input.contains('<')) loop(input.tail, acc)
      else acc

    if (!input.contains('<') && !input.contains('>')) List.empty
    loop(input, List.empty)
  }

  private def placeholderStarts(input: String) =
    if (input.head == '<' && input.contains('>')) {
      !input.substring(0, input.indexOfSlice(">")).contains(' ')
    } else false

  private def resolvePlaceholder(input: String)(session: Session): Xor[ResolverError, String] = {
    val (key, index) = parseStackedKey(input.substring(1, input.length - 1))
    key match {
      case "random-uuid"             ⇒ right(UUID.randomUUID().toString)
      case "random-positive-integer" ⇒ right(scala.util.Random.nextInt(100).toString)
      case "random-string"           ⇒ right(scala.util.Random.nextString(5))
      case "random-boolean"          ⇒ right(scala.util.Random.nextBoolean().toString)
      case "timestamp"               ⇒ right((System.currentTimeMillis / 1000).toString)
      case other: String ⇒
        extractors.get(other).fold[Xor[ResolverError, String]] {
          session.getOpt(other, index).map(right).getOrElse(left(SimpleResolverError(other, session)))
        } { mapper ⇒
          Try {
            session.getOpt(mapper.key, index).map { valueToMap ⇒
              mapper.transform(valueToMap)
            }
          } match {
            case Success(value) ⇒ value.map(right).getOrElse(left(SimpleResolverError(other, session)))
            case Failure(e) ⇒
              left(ExtractorResolverError(other, session, e))
          }
        }
    }
  }

  def parseStackedKey(key: String): (String, Option[Int]) =
    parseIndice(key).fold[(String, Option[Int])]((key, None)) { indexStr ⇒
      Try { indexStr.toInt } match {
        case Failure(_) ⇒ (key, None) // FIXME should bubble up?
        case Success(index) ⇒
          val keyName = key.dropRight(indexStr.length + 2)
          (keyName, Some(index))
      }
    }

  def parseIndice(key: String): Option[String] = {
    key.reverse.headOption.flatMap { head ⇒
      if (head == ']')
        Some(key.reverse.drop(1).takeWhile(_ != '[').reverse)
      else
        None
    }
  }

  // TODO should accumulate errors
  def fillPlaceholders(input: String)(session: Session): Xor[ResolverError, String] = {
    def loop(placeholders: List[String], acc: String): Xor[ResolverError, String] = {
      placeholders.headOption.fold[Xor[ResolverError, String]](right(acc)) { ph ⇒
        for {
          resolvedValue ← resolvePlaceholder(ph)(session)
          res ← loop(placeholders.tail, acc.replace(ph, resolvedValue))
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

case class Mapper(key: String, transform: String ⇒ String)