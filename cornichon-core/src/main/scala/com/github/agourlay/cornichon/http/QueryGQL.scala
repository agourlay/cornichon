package com.github.agourlay.cornichon.http

import cats.syntax.show._
import io.circe.{ Encoder, Json }
import sangria.ast.Document
import sangria.renderer.QueryRenderer

case class QueryGQL(
    url: String,
    query: Document,
    operationName: Option[String],
    variables: Option[Map[String, Json]],
    params: Seq[(String, String)],
    headers: Seq[(String, String)]) {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  def withQuery(query: Document) = copy(query = query)

  def withOperationName(operationName: String) = copy(operationName = Some(operationName))

  def withVariables(newVariables: (String, VarValue)*) = {
    val vars: Map[String, Json] = newVariables.map { case (k, v) ⇒ k → v.value }(scala.collection.breakOut)
    copy(variables = variables.fold(Some(vars))(v ⇒ Some(v ++ vars)))
  }

  lazy val querySource = query.source.getOrElse(QueryRenderer.render(query, QueryRenderer.Pretty))

  lazy val payload: String = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    GqlPayload(querySource, operationName, variables).asJson.show
  }

  private case class GqlPayload(query: String, operationName: Option[String], variables: Option[Map[String, Json]])

}

trait VarValue {
  def value: Json
}

object VarValue {
  implicit def fromEncoder[A: Encoder](a: A): VarValue = new VarValue {
    def value: Json = Encoder[A].apply(a)
  }
}

object QueryGQL {
  val emptyDocument = Document(Vector.empty)
}

