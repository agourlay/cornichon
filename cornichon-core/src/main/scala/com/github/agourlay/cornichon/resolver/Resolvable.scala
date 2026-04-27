package com.github.agourlay.cornichon.resolver

import java.util.UUID

import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json

import scala.annotation.implicitNotFound

@implicitNotFound("No instance of typeclass Resolvable found for type ${A} - this instance is required if you are trying to use ${A} as custom HTTP body type")
trait Resolvable[A] {

  def toResolvableForm(r: A): String
  def fromResolvableForm(r: String): A

  // Cheap fast-path: may `r` contain a placeholder marker ('<')?
  // Default scans the serialized form; override when a check cheaper than serialization is possible.
  def mayContainPlaceholders(r: A): Boolean =
    toResolvableForm(r).indexOf('<') >= 0

  def transformResolvableForm(r: A)(transf: String => String): A = {
    val rf = toResolvableForm(r)
    val updated = transf(rf)
    // If the transformation function had no effect
    // we can return the original value directly
    // and avoid an extra transformation from the resolved form
    if (updated == rf) r else fromResolvableForm(updated)
  }

}

object Resolvable {

  def apply[A](implicit resolvable: Resolvable[A]): Resolvable[A] = resolvable

  implicit val stringResolvable: Resolvable[String] = new Resolvable[String] {
    def toResolvableForm(s: String) = s
    def fromResolvableForm(s: String) = s
    override def mayContainPlaceholders(s: String): Boolean = s.indexOf('<') >= 0
  }

  // Primitives can never contain '<' in their toString form — skip the toString allocation entirely.
  implicit val booleanResolvable: Resolvable[Boolean] = new Resolvable[Boolean] {
    def toResolvableForm(b: Boolean) = b.toString
    def fromResolvableForm(b: String) = java.lang.Boolean.parseBoolean(b)
    override def mayContainPlaceholders(b: Boolean): Boolean = false
  }

  implicit val intResolvable: Resolvable[Int] = new Resolvable[Int] {
    def toResolvableForm(i: Int) = i.toString
    def fromResolvableForm(i: String) = java.lang.Integer.parseInt(i)
    override def mayContainPlaceholders(i: Int): Boolean = false
  }

  implicit val shortResolvable: Resolvable[Short] = new Resolvable[Short] {
    def toResolvableForm(s: Short) = s.toString
    def fromResolvableForm(s: String) = java.lang.Short.parseShort(s)
    override def mayContainPlaceholders(s: Short): Boolean = false
  }

  implicit val doubleResolvable: Resolvable[Double] = new Resolvable[Double] {
    def toResolvableForm(d: Double) = d.toString
    def fromResolvableForm(d: String) = java.lang.Double.parseDouble(d)
    override def mayContainPlaceholders(d: Double): Boolean = false
  }

  implicit val floatResolvable: Resolvable[Float] = new Resolvable[Float] {
    def toResolvableForm(f: Float) = f.toString
    def fromResolvableForm(f: String) = java.lang.Float.parseFloat(f)
    override def mayContainPlaceholders(f: Float): Boolean = false
  }

  implicit val longResolvable: Resolvable[Long] = new Resolvable[Long] {
    def toResolvableForm(l: Long) = l.toString
    def fromResolvableForm(l: String) = java.lang.Long.parseLong(l)
    override def mayContainPlaceholders(l: Long): Boolean = false
  }

  implicit val bigDecResolvable: Resolvable[BigDecimal] = new Resolvable[BigDecimal] {
    def toResolvableForm(b: BigDecimal) = b.toString
    def fromResolvableForm(b: String) = BigDecimal(b)
    override def mayContainPlaceholders(b: BigDecimal): Boolean = false
  }

  implicit val uuidResolvable: Resolvable[UUID] = new Resolvable[UUID] {
    def toResolvableForm(u: UUID) = u.toString
    def fromResolvableForm(u: String) = UUID.fromString(u)
    override def mayContainPlaceholders(u: UUID): Boolean = false
  }

  implicit val jsonResolvable: Resolvable[Json] = new Resolvable[Json] {
    def toResolvableForm(j: Json) = j.spaces2
    def fromResolvableForm(j: String) = CornichonJson.parseDslJsonUnsafe(j)
    override def mayContainPlaceholders(j: Json): Boolean = CornichonJson.jsonContainsChar(j, '<')
  }

}
