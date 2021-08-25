package com.github.agourlay.cornichon.resolver

import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ RandomContext, Session }
import com.github.agourlay.cornichon.resolver.PlaceholderResolver._
import munit.FunSuite

class PlaceholderResolverSpec extends FunSuite {

  private val rc = RandomContext.fromSeed(1L)
  private val noExtractor = Map.empty[String, Mapper]

  test("fillPlaceholders not be confused if key contains empty string") {
    val session = Session.newEmpty.addValueUnsafe("project-name", "cornichon")
    val content = "This project is named <project name>"
    assert(fillPlaceholders(content)(session, rc, noExtractor) == Right("This project is named <project name>"))
  }

  test("fillPlaceholders not be confused by markup language") {
    val session = Session.newEmpty.addValueUnsafe("pi", "3.14")
    val content = "PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT <pi>"
    assert(fillPlaceholders(content)(session, rc, noExtractor) == Right("PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT 3.14"))
  }

  test("fillPlaceholders fail with clear error message if key is defined in both Session and Extractors") {
    val extractor = JsonMapper("customer", "id")
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty.addValueUnsafe("customer-id", "12345")
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors) == Left(AmbiguousKeyDefinition("customer-id")))
  }

  test("fillPlaceholders fail with clear error message if key defined in the extractor is not in Session") {
    val extractor = JsonMapper("customer", "id")
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors).leftMap(_.renderedMessage) == Left("Error occurred while running Mapper attached to key 'customer-id'\ncaused by:\nkey 'customer' can not be found in session\nempty"))
  }

  test("fillPlaceholders use registered SimpleMapper") {
    val extractor = SimpleMapper(() => "magic!")
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors) == Right("magic!"))
  }

  test("fillPlaceholders use registered TextMapper") {
    val extractor = TextMapper("customer", customerString => customerString.length.toString)
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty.addValueUnsafe("customer", "my-customer-name-of-great-length")
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors) == Right("32"))
  }

  test("fillPlaceholders use registered HistoryMapper") {
    val extractor = HistoryMapper("customer", customers => customers.length.toString)
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty.addValuesUnsafe(
      "customer" -> "customer1",
      "customer" -> "customer2",
      "customer" -> "customer3"
    )
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors) == Right("3"))
  }

  test("fillPlaceholders use registered SessionMapper") {
    val extractor = SessionMapper(s => s.get("other-thing"))
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty.addValueUnsafe("other-thing", "other unrelated value")
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors) == Right("other unrelated value"))
  }

  test("fillPlaceholders use registered RandomMapper") {
    val extractor = RandomMapper(rd => rd.alphanumeric(5).length.toString)
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors) == Right("5"))
  }

  test("fillPlaceholders use registered JsonMapper") {
    val extractor = JsonMapper("customer", "id")
    val extractors = Map("customer-id" -> extractor)
    val s = Session.newEmpty.addValueUnsafe("customer", """{"id" : "122"}""")
    assert(fillPlaceholders("<customer-id>")(s, rc, extractors) == Right("122"))
  }

  test("fillPlaceholders <scenario-unique-number> starts with 1") {
    val session = Session.newEmpty
    val content = "<scenario-unique-number>"
    assert(fillPlaceholders(content)(session, RandomContext.fromSeed(1L), noExtractor) == Right("1"))
  }

  test("fillPlaceholders generates unique numbers scoped to the RandomContext with <scenario-unique-number>") {
    val session = Session.newEmpty
    val content = "<scenario-unique-number>"
    val rc = RandomContext.fromSeed(1L)
    val max = 100
    for (i <- 1 until max) {
      assert(fillPlaceholders(content)(session, rc, noExtractor) == Right(i.toString))
    }
    assert(rc.uniqueLong() == max)
    // a different RandomContext is not impacted
    assert(fillPlaceholders(content)(session, RandomContext.fromSeed(1L), noExtractor) == Right("1"))
  }

  test("fillPlaceholders <global-unique-number> starts with 1 and is scoped globally") {
    val session = Session.newEmpty
    val content = "<global-unique-number>"
    assert(fillPlaceholders(content)(session, RandomContext.fromSeed(1L), noExtractor) == Right("1"))
    val rc = RandomContext.fromSeed(1L)
    val max = 100
    for (i <- 2 until max) {
      assert(fillPlaceholders(content)(session, rc, noExtractor) == Right(i.toString))
    }
    // the RandomContext is not impacted
    assert(rc.uniqueLong() == 1L)
  }
}
