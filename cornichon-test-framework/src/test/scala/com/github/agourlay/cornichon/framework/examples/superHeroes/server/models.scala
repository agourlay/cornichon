package com.github.agourlay.cornichon.framework.examples.superHeroes.server

case class Publisher(name: String, foundationYear: Int, location: String)

case class SuperHero(name: String, realName: String, city: String, hasSuperpowers: Boolean, publisher: Publisher)

trait ApiError {
  def id: String
  def msg: String
}

case class SessionNotFound(id: String) extends ApiError {
  val msg = s"Session $id not found"
}

case class PublisherNotFound(id: String) extends ApiError {
  val msg = s"Publisher $id not found"
}

case class SuperHeroNotFound(id: String) extends ApiError {
  val msg = s"Superhero $id not found"
}

case class PublisherAlreadyExists(id: String) extends ApiError {
  val msg = s"Publisher $id already exist"
}

case class SuperHeroAlreadyExists(id: String) extends ApiError {
  val msg = s"Publisher $id already exist"
}

case class HttpError(error: String) extends AnyVal
object HttpError {

  implicit val httpErrorCodec: io.circe.Codec[HttpError] = {
    val decoder: io.circe.Decoder[HttpError] =
      (c: io.circe.HCursor) => c.downField("error").as[String].map(HttpError.apply)
    val encoder: io.circe.Encoder[HttpError] =
      (a: HttpError) => io.circe.Json.obj("error" -> io.circe.Json.fromString(a.error))

    io.circe.Codec.from(decoder, encoder)
  }

}