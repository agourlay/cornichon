package com.github.agourlay.cornichon.resolver

import com.github.agourlay.cornichon.core.Session
import org.parboiled2.{ CharPredicate, Parser, ParserInput }

class PlaceholderParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    Ignore ~ zeroOrMore(PlaceholderRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  def PlaceholderRule = rule('<' ~ PlaceholderTXT ~ optIndex ~ '>' ~> Placeholder)

  def optIndex = rule(optional('[' ~ Number ~ ']'))

  def PlaceholderTXT = rule(capture(oneOrMore(CharPredicate.Visible -- Session.notAllowedInKey)))

  def Ignore = rule { zeroOrMore(!PlaceholderRule ~ ANY) }

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

case class Placeholder(key: String, index: Option[Int]) {
  val fullKey = index.fold(s"<$key>") { index ⇒ s"<$key[$index]>" }
}