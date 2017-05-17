package com.github.agourlay.cornichon.matchers

import org.parboiled2.{ CharPredicate, Parser, ParserInput }

class MatcherParser(val input: ParserInput) extends Parser {

  def matchersRule = rule {
    Ignore ~ zeroOrMore(MatcherRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  def MatcherRule = rule("*" ~ MatcherTXT ~ "*" ~> MatcherKey)

  def MatcherTXT = rule(capture(oneOrMore(CharPredicate.Visible -- MatcherParser.notAllowedInKey)))

  def Ignore = rule { zeroOrMore(!"*" ~ ANY) }

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

object MatcherParser {
  val notAllowedInKey = "\r\n<>[]* "
}

case class MatcherKey(key: String) extends AnyVal