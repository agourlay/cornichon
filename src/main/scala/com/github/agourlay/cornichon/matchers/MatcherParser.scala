package com.github.agourlay.cornichon.matchers

import org.parboiled2.{ CharPredicate, Parser, ParserInput }

class MatcherParser(val input: ParserInput) extends Parser {

  def matchersRule = rule {
    Ignore ~ zeroOrMore(MatcherRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  def MatcherRule = rule("<<" ~ MatcherTXT ~ ">>" ~> Matcher)

  val notAllowedInKey = "\r\n<>[] "

  def MatcherTXT = rule(capture(oneOrMore(CharPredicate.Visible -- notAllowedInKey)))

  def Ignore = rule { zeroOrMore(!'<' ~ ANY) }

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

case class Matcher(key: String) {
  val fullKey = s"<<$key>>"
}