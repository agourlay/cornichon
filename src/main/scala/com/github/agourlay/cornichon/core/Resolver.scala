package com.github.agourlay.cornichon.core

import java.util.UUID

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.annotation.tailrec

class Resolver {

  private def findPlaceHolders(input: String): List[String] = {
    @tailrec
    def loop(input: String, acc: List[String]): List[String] =
      if (input.isEmpty) acc
      else if (input.head == '<' && input.contains('>')) {
        val placeHolder = input.takeWhile(_ != '>') + ">"
        loop(input.drop(placeHolder.length), acc.::(placeHolder))
      } else if (input.contains('<')) loop(input.tail, acc)
      else acc

    // could be in the wrong orders but good enough
    if (input.count(_ == '<') != input.count(_ == '>')) List.empty
    else loop(input, List.empty)
  }

  private def resolvePlaceholder(input: String)(source: Map[String, String]): Xor[ResolverError, String] = {
    val cleanInput = input.substring(1, input.length - 1)
    if (cleanInput == "random-uuid") right(UUID.randomUUID().toString)
    else source.get(cleanInput.toString).map(right).getOrElse(left(ResolverError(cleanInput)))
  }

  def fillPlaceHolder(input: String)(source: Map[String, String]): Xor[ResolverError, String] = {
    def loop(placeholders: List[String], acc: String): Xor[ResolverError, String] = {
      if (placeholders.isEmpty) right(acc)
      else {
        val ph = placeholders.head
        resolvePlaceholder(ph)(source).fold(e ⇒ left(e), resolvedValue ⇒
          loop(placeholders.tail, acc.replace(ph, resolvedValue))
        )
      }
    }
    loop(findPlaceHolders(input), input)
  }
}