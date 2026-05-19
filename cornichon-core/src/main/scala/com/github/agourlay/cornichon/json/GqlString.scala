package com.github.agourlay.cornichon.json

import cats.Show
import com.github.agourlay.cornichon.json.CornichonJson.parseGraphQLJson
import com.github.agourlay.cornichon.resolver.Resolvable
import io.circe.Encoder

// GqlString must remain structurally distinct from String at runtime:
// CornichonJson.parseDslJson dispatches via `case s: String =>` to choose
// strict-JSON parsing vs the lenient GraphQL Encoder. An opaque-type GqlString
// would erase to String and silently route GraphQL bodies through the strict parser.
case class GqlString(input: String) extends AnyVal

object GqlString {

  implicit val gqlResolvableForm: Resolvable[GqlString] = new Resolvable[GqlString] {
    def toResolvableForm(g: GqlString): String = g.input
    def fromResolvableForm(s: String): GqlString = GqlString(s)
  }

  implicit val gqlShow: Show[GqlString] =
    Show.show[GqlString](g => s"GraphQl JSON ${g.input}")

  implicit val gqlEncode: Encoder[GqlString] =
    Encoder.instance[GqlString](g => parseGraphQLJson(g.input).valueUnsafe)

}
