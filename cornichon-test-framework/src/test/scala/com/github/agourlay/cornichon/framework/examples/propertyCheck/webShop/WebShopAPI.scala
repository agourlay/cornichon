package com.github.agourlay.cornichon.framework.examples.propertyCheck.webShop

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import java.util.UUID
import java.util.concurrent.TimeUnit
import com.github.agourlay.cornichon.framework.examples.HttpServer
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class WebShopAPI(maxSyncDelay: FiniteDuration) extends Http4sDsl[IO] {

  case class Product(id: UUID, name: String, description: String, price: BigInt)
  object Product {
    def fromDraft(id: UUID, d: ProductDraft) =
      Product(id, d.name, d.description, d.price)
  }

  private val productsDB = new TrieMap[UUID, Product]()
  private val productsSearch = new TrieMap[UUID, Product]()

  private val r = new scala.util.Random()

  def randomDelay(): FiniteDuration = {
    val randomSeconds = r.nextInt(maxSyncDelay.toSeconds.toInt)
    Duration.apply(randomSeconds.toLong, TimeUnit.SECONDS)
  }

  object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")
  object DescriptionContainsQueryParamMatcher extends QueryParamDecoderMatcher[String]("descriptionContains")

  def indexInSearchWithDelay(p: Product): IO[Product] =
    IO.delay {
      productsSearch.remove(p.id)
      productsSearch.put(p.id, p)
      p
    }.delayBy(randomDelay())

  def removeFromSearchIndexWithDelay(p: Product): IO[Product] =
    IO.delay {
      productsSearch.remove(p.id)
      p
    }.delayBy(randomDelay())

  private val productsService = HttpRoutes.of[IO] {
    case GET -> Root / "products" =>
      Ok(productsDB.values.asJson)

    case GET -> Root / "products" / UUIDVar(id) =>
      productsDB.get(id) match {
        case None    => NotFound()
        case Some(p) => Ok(p.asJson)
      }

    case DELETE -> Root / "products" / UUIDVar(id) =>
      productsDB.get(id) match {
        case None =>
          NotFound()
        case Some(p) =>
          productsDB.remove(p.id)
          removeFromSearchIndexWithDelay(p).unsafeToFuture()
          Ok(s"product ${p.id} deleted")
      }

    case req @ POST -> Root / "products" =>
      for {
        pd <- req.as[ProductDraft]
        pid = UUID.randomUUID()
        p = Product.fromDraft(pid, pd)
        _ <- IO.delay(productsDB.put(pid, p))
        resp <- Created(p.asJson)
      } yield {
        indexInSearchWithDelay(p).unsafeToFuture()
        resp
      }

    case req @ POST -> Root / "products" / UUIDVar(pid) =>
      if (!productsDB.contains(pid))
        NotFound(s"product $pid not found")
      else
        for {
          pd <- req.as[ProductDraft]
          p = Product.fromDraft(pid, pd)
          _ <- IO.delay {
            productsDB.remove(pid)
            productsDB.put(pid, p)
          }
          resp <- Created(p.asJson)
        } yield {
          indexInSearchWithDelay(p).unsafeToFuture()
          resp
        }

    case GET -> Root / "products-search" =>
      Ok(productsSearch.values.asJson)

    case GET -> Root / "products-search" :? NameQueryParamMatcher(name) =>
      val l = productsSearch.values.iterator.filter(_.name == name).toList
      Ok(l.asJson)

    case GET -> Root / "products-search" :? DescriptionContainsQueryParamMatcher(descriptionContains) =>
      val l = productsSearch.values.iterator.filter(_.description.contains(descriptionContains)).toList
      Ok(l.asJson)

    case POST -> Root / "products-search" / "reindex" =>
      productsSearch.clear()
      productsDB.values.foreach(p => productsSearch.put(p.id, p))
      Ok(s"index refreshed")
  }

  private val routes = Router(
    "/" -> productsService
  )

  def start(httpPort: Int): Future[HttpServer] =
    BlazeServerBuilder[IO]
      .bindHttp(httpPort, "localhost")
      .withoutBanner
      .withHttpApp(routes.orNotFound)
      .allocated
      .map { case (_, stop) => new HttpServer(stop) }
      .unsafeToFuture()
}

case class ProductDraft(name: String, description: String, price: BigInt)

object ProductDraft {
  implicit val productDraftJsonDecoder: EntityDecoder[IO, ProductDraft] = jsonOf[IO, ProductDraft]
}
