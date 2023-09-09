package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.matchers.Matcher
import com.github.agourlay.cornichon.resolver.Resolvable

trait ScenarioContext {
  // RunState
  val randomContext: RandomContext
  val session: Session

  // PlaceholderResolver
  def fillPlaceholders[A: Resolvable](input: A): Either[CornichonError, A]
  def fillPlaceholders(input: String): Either[CornichonError, String]
  def fillSessionPlaceholders(input: String): Either[CornichonError, String]
  def fillPlaceholders(params: Seq[(String, String)]): Either[CornichonError, List[(String, String)]]

  // MatcherResolver
  def findAllMatchers(input: String): Either[CornichonError, Vector[Matcher]]
}

object ScenarioContext {
  private val rightEmptyVec = Right(Vector.empty)
  val empty: ScenarioContext = new ScenarioContext {
    val randomContext: RandomContext = RandomContext.fromSeed(1L)
    val session: Session = Session.newEmpty

    def fillPlaceholders[A: Resolvable](input: A): Either[CornichonError, A] = Right(input)
    def fillPlaceholders(input: String): Either[CornichonError, String] = Right(input)
    def fillSessionPlaceholders(input: String): Either[CornichonError, String] = Right(input)
    def fillPlaceholders(params: Seq[(String, String)]): Either[CornichonError, List[(String, String)]] = Right(params.toList)

    def findAllMatchers(input: String): Either[CornichonError, Vector[Matcher]] = rightEmptyVec
  }
}