package com.github.agourlay.cornichon.resolver

import java.net.URL
import java.util.UUID

import cats.syntax.option._
import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json

import scala.annotation.implicitNotFound

@implicitNotFound("No instance of typeclass Resolvable found for type ${A} - this instance is required if you are trying to use ${A} as custom HTTP body type")
trait Resolvable[A] {

  def toResolvableForm(r: A): Option[String]
  def fromResolvableForm(r: String): A

  def transformResolvableForm(r: A)(transf: String => String): A = {
    toResolvableForm(r) match {
      case None => r
      case Some(rf) =>
        val updated = transf(rf)
        // If the transformation function had no effect
        // we can return the original value directly
        // and avoid an extra transformation from the resolved form
        if (updated == rf) r else fromResolvableForm(updated)
    }
  }

}

object Resolvable {

  def apply[A](implicit resolvable: Resolvable[A]): Resolvable[A] = resolvable

  implicit val stringResolvable = new Resolvable[String] {
    def toResolvableForm(s: String) = s.some
    def fromResolvableForm(s: String) = s
  }

  implicit val booleanResolvable = new Resolvable[Boolean] {
    def toResolvableForm(b: Boolean) = b.toString.some
    def fromResolvableForm(b: String) = java.lang.Boolean.parseBoolean(b)
  }

  implicit val intResolvable = new Resolvable[Int] {
    def toResolvableForm(i: Int) = i.toString.some
    def fromResolvableForm(i: String) = java.lang.Integer.parseInt(i)
  }

  implicit val shortResolvable = new Resolvable[Short] {
    def toResolvableForm(s: Short) = s.toString.some
    def fromResolvableForm(s: String) = java.lang.Short.parseShort(s)
  }

  implicit val doubleResolvable = new Resolvable[Double] {
    def toResolvableForm(d: Double) = d.toString.some
    def fromResolvableForm(d: String) = java.lang.Double.parseDouble(d)
  }

  implicit val floatResolvable = new Resolvable[Float] {
    def toResolvableForm(f: Float) = f.toString.some
    def fromResolvableForm(f: String) = java.lang.Float.parseFloat(f)
  }

  implicit val longResolvable = new Resolvable[Long] {
    def toResolvableForm(l: Long) = l.toString.some
    def fromResolvableForm(l: String) = java.lang.Long.parseLong(l)
  }

  implicit val bigDecResolvable = new Resolvable[BigDecimal] {
    def toResolvableForm(b: BigDecimal) = b.toString.some
    def fromResolvableForm(b: String) = BigDecimal(b)
  }

  implicit val uuidResolvable = new Resolvable[UUID] {
    def toResolvableForm(u: UUID) = u.toString.some
    def fromResolvableForm(u: String) = UUID.fromString(u)
  }

  implicit val jsonResolvable = new Resolvable[Json] {
    def toResolvableForm(j: Json) = j.spaces2.some
    def fromResolvableForm(j: String) = CornichonJson.parseDslJsonUnsafe(j)
  }

  implicit def listTupleStringResolvable = new Resolvable[List[(String, String)]] {
    def toResolvableForm(s: List[(String, String)]) = {
      val values = s.iterator
        .map { case (k, v) => (k, Json.fromString(v)) }
        .toList
      Json.fromFields(values).spaces2.some
    }
    def fromResolvableForm(u: String) =
      CornichonJson.parseString(u)
        .valueUnsafe // should be a valid JSON :s
        .asObject
        .get // should be an Object :s
        .toIterable
        .map { case (k, v) => (k, CornichonJson.jsonStringValue(v)) }
        .toList
  }

  implicit val urlResolvable = new Resolvable[URL] {
    def toResolvableForm(u: URL) = None
    def fromResolvableForm(u: String) = new URL(u)
  }

}