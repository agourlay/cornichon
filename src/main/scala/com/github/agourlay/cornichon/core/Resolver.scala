package com.github.agourlay.cornichon.core

import java.util.UUID

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.annotation.tailrec

object Resolver {

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

    // could be in the wrong orders but good enough
    if (input.count(_ == '<') != input.count(_ == '>')) List.empty
    else loop(input, List.empty)
  }

  private def placeholderStarts(input: String) =
    if (input.head == '<' && input.contains('>')) {
      !input.substring(0, input.indexOfSlice(">")).contains(' ')
    } else false

  private def resolvePlaceholder(input: String)(source: Map[String, String]): Xor[ResolverError, String] =
    input.substring(1, input.length - 1) match {
      case "random-uuid"             ⇒ right(UUID.randomUUID().toString)
      case "random-positive-integer" ⇒ right(scala.util.Random.nextInt(100).toString)
      case "random-string"           ⇒ right(scala.util.Random.nextString(5))
      case other: String             ⇒ source.get(other).map(right).getOrElse(left(ResolverError(other)))
    }

  // TODO should accumulate errors
  def fillPlaceholders(input: String)(source: Map[String, String]): Xor[ResolverError, String] = {
    def loop(placeholders: List[String], acc: String): Xor[ResolverError, String] = {
      if (placeholders.isEmpty) right(acc)
      else {
        val ph = placeholders.head
        resolvePlaceholder(ph)(source).fold(e ⇒ left(e), resolvedValue ⇒
          loop(placeholders.tail, acc.replace(ph, resolvedValue)))
      }
    }
    loop(findPlaceholders(input), input)
  }

  def fillPlaceholdersUnsafe(input: String)(source: Map[String, String]): String =
    fillPlaceholders(input)(source).fold(e ⇒ throw e, identity)

  // TODO accumulate errors
  def tuplesResolver(params: Seq[(String, String)], session: Session): Xor[ResolverError, Seq[(String, String)]] = {
    def loop(params: Seq[(String, String)], session: Session, acc: Seq[(String, String)]): Xor[ResolverError, Seq[(String, String)]] = {
      if (params.isEmpty) right(acc)
      else {
        val (name, value) = params.head
        fillPlaceholders(name)(session.content).fold(left, resolvedName ⇒ {
          fillPlaceholders(value)(session.content).fold(left, resolvedValue ⇒ {
            loop(params.tail, session, acc :+ (resolvedName, resolvedValue))
          })
        })
      }
    }
    loop(params, session, Seq.empty[(String, String)])
  }

}