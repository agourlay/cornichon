package com.github.agourlay.cornichon.json

import cats.Show
import com.github.agourlay.cornichon.json.CornichonJson.parseGraphQLJson
import com.github.agourlay.cornichon.resolver.Resolvable
import io.circe.Encoder

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
