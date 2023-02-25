package com.github.agourlay.cornichon.util

object TraverseUtils {
  private val rightNil = Right(Nil)
  private val rightVecEmpty = Right(Vector.empty)

  def traverse[A, B, E](elements: List[A])(f: A => Either[E, B]): Either[E, List[B]] = {
    elements match {
      case Nil         => rightNil
      case head :: Nil => f(head).map(_ :: Nil)
      case _ =>
        val listBuffer = List.newBuilder[B]
        for (elem <- elements) {
          f(elem) match {
            case e @ Left(_) => return e.asInstanceOf[Either[E, List[B]]]
            case Right(b)    => listBuffer += b
          }
        }
        Right(listBuffer.result())
    }
  }

  def traverseIL[A, B, E](elements: Iterator[A])(f: A => Either[E, B]): Either[E, List[B]] = {
    if (elements.hasNext) {
      val listBuffer = List.newBuilder[B]
      for (elem <- elements) {
        f(elem) match {
          case e @ Left(_) => return e.asInstanceOf[Either[E, List[B]]]
          case Right(b)    => listBuffer += b
        }
      }
      Right(listBuffer.result())
    } else {
      rightNil
    }
  }

  def traverseLO[A, B](elements: List[A])(f: A => Option[B]): Option[List[B]] = {
    elements match {
      case Nil         => None
      case head :: Nil => f(head).map(_ :: Nil)
      case _ =>
        val listBuffer = List.newBuilder[B]
        for (elem <- elements) {
          f(elem) match {
            case None    => return None
            case Some(b) => listBuffer += b
          }
        }
        Some(listBuffer.result())
    }
  }

  def traverse[A, B, E](elements: Vector[A])(f: A => Either[E, B]): Either[E, Vector[B]] = {
    val len = elements.length
    if (len == 0) {
      rightVecEmpty
    } else if (len == 1) {
      f(elements(0)).map(Vector(_))
    } else {
      val vectorBuilder = Vector.newBuilder[B]
      var i = 0
      while (i < len) {
        f(elements(i)) match {
          case e @ Left(_) => return e.asInstanceOf[Either[E, Vector[B]]]
          case Right(b)    => vectorBuilder += b
        }
        i += 1
      }
      Right(vectorBuilder.result())
    }
  }

  def traverseIV[A, B, E](elements: Iterator[A])(f: A => Either[E, B]): Either[E, Vector[B]] = {
    if (elements.hasNext) {
      val vectorBuilder = Vector.newBuilder[B]
      for (e <- elements) {
        f(e) match {
          case e @ Left(_) => return e.asInstanceOf[Either[E, Vector[B]]]
          case Right(b)    => vectorBuilder += b
        }
      }
      Right(vectorBuilder.result())
    } else {
      rightVecEmpty
    }
  }
}