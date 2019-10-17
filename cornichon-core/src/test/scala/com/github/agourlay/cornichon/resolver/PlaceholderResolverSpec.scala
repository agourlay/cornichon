package com.github.agourlay.cornichon.resolver

import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ RandomContext, Session }
import org.scalatest.{ Matchers, OptionValues, WordSpec }

class PlaceholderResolverSpec extends WordSpec
  with Matchers
  with OptionValues {

  private val rc = RandomContext.fromSeed(1L)
  private val noExtractor = Map.empty[String, Mapper]

  "Resolver" when {
    "fillPlaceholders" must {
      "not be confused if key contains empty string" in {
        val session = Session.newEmpty.addValueUnsafe("project-name", "cornichon")
        val content = "This project is named <project name>"
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Right("This project is named <project name>"))
      }

      "not be confused by unclosed markup used in a math context" in {
        val session = Session.newEmpty.addValueUnsafe("pi", "3.14")
        val content = "3.15 > <pi>"
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Right("3.15 > 3.14"))
      }

      "not be confused by markup language" in {
        val session = Session.newEmpty.addValueUnsafe("pi", "3.14")
        val content = "PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT <pi>"
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Right("PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT 3.14"))
      }

      "generate random positive integer if <random-positive-integer> - fixed by seed" in {
        val session = Session.newEmpty
        val content = "<random-positive-integer>"
        val fixedRc = RandomContext.fromSeed(1L)
        val integerInSession = PlaceholderResolver.fillPlaceholders(content)(session, fixedRc, noExtractor).valueUnsafe
        integerInSession.toInt should be(8985)
      }

      "generate random string if <random-string> - fixed by seed" in {
        val session = Session.newEmpty
        val content = "<random-string>"
        val fixedRc = RandomContext.fromSeed(1L)
        val stringInSession = PlaceholderResolver.fillPlaceholders(content)(session, fixedRc, noExtractor).valueUnsafe
        stringInSession should be("ƛණ㕮銙혁")
      }

      "generate random alphanum string if <random-alphanum-string> - fixed by seed" in {
        val session = Session.newEmpty
        val content = "<random-alphanum-string>"
        val fixedRc = RandomContext.fromSeed(2L)
        val stringInSession = PlaceholderResolver.fillPlaceholders(content)(session, fixedRc, noExtractor).valueUnsafe
        stringInSession should be("oC8rH")
      }

      "generate random boolean if <random-boolean> - fixed by seed" in {
        val session = Session.newEmpty
        val content = "<random-boolean>"
        val fixedRc = RandomContext.fromSeed(1L)
        val booleanInSession = PlaceholderResolver.fillPlaceholders(content)(session, fixedRc, noExtractor).valueUnsafe
        booleanInSession.toBoolean should be(true)
      }

      "generate random timestamp string if <random-timestamp>" in {
        val session = Session.newEmpty
        val content = "<random-timestamp>"
        val timestampInSession = PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor).valueUnsafe
        // not sure what to assert here
        noException should be thrownBy new java.util.Date(timestampInSession.toLong * 1000L)
      }

      "generate current timestamp string if <current-timestamp>" in {
        val session = Session.newEmpty
        val content = "<current-timestamp>"
        val timestampInSession = PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor).valueUnsafe
        val date = new java.util.Date(timestampInSession.toLong * 1000L)
        date.before(new java.util.Date()) should be(true)
      }

      "fail with clear error message if key is defined in both Session and Extractors" in {
        val extractor = JsonMapper("customer", "id")
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty.addValueUnsafe("customer-id", "12345")
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors) should be(Left(AmbiguousKeyDefinition("customer-id")))
      }

      "fail with clear error message if key defined in the extractor is not in Session" in {
        val extractor = JsonMapper("customer", "id")
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors).leftMap(_.renderedMessage) should be(Left("Error occurred while running Mapper attached to key 'customer-id'\ncaused by:\nkey 'customer' can not be found in session \nempty"))
      }

      "use registered SimpleMapper" in {
        val extractor = SimpleMapper(() ⇒ "magic!")
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors) should be(Right("magic!"))
      }

      "use registered TextMapper" in {
        val extractor = TextMapper("customer", customerString ⇒ customerString.length.toString)
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty.addValueUnsafe("customer", "my-customer-name-of-great-length")
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors) should be(Right("32"))
      }

      "use registered HistoryMapper" in {
        val extractor = HistoryMapper("customer", customers ⇒ customers.length.toString)
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty.addValuesUnsafe(
          "customer" -> "customer1",
          "customer" -> "customer2",
          "customer" -> "customer3"
        )
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors) should be(Right("3"))
      }

      "use registered SessionMapper" in {
        val extractor = SessionMapper(s ⇒ s.get("other-thing"))
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty.addValueUnsafe("other-thing", "other unrelated value")
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors) should be(Right("other unrelated value"))
      }

      "use registered RandomMapper" in {
        val extractor = RandomMapper(rd ⇒ rd.alphanumeric.take(5).mkString("").length.toString)
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors) should be(Right("5"))
      }

      "use registered JsonMapper" in {
        val extractor = JsonMapper("customer", "id")
        val extractors = Map("customer-id" → extractor)
        val s = Session.newEmpty.addValueUnsafe("customer", """{"id" : "122"}""")
        PlaceholderResolver.fillPlaceholders("<customer-id>")(s, rc, extractors) should be(Right("122"))
      }
    }
  }
}
