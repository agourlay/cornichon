package com.github.agourlay.cornichon.dsl

class DslListBuilder[A] {

  private val elmts = collection.mutable.ArrayBuffer.empty[A]

  def elements = elmts.toVector

  def addElmt(s: A) = {
    elmts += s
    this
  }
}