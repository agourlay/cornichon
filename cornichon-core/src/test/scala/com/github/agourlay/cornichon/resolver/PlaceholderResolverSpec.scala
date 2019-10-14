package com.github.agourlay.cornichon.resolver

import java.util.UUID

import com.github.agourlay.cornichon.core.SessionSpec._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ KeyNotFoundInSession, RandomContext, Session }
import org.scalacheck.Gen
import org.scalatest.{ Matchers, OptionValues, WordSpec }
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class PlaceholderResolverSpec extends WordSpec
  with Matchers
  with OptionValues
  with ScalaCheckPropertyChecks {

  private val rc = RandomContext.fromSeed(1L)
  private val noExtractor = Map.empty[String, Mapper]

  "Resolver" when {
    "findPlaceholders" must {
      "find placeholder in content solely containing a placeholder without index" in {
        forAll(keyGen) { key ⇒
          PlaceholderResolver.findPlaceholders(s"<$key>") should be(Right(List(Placeholder(key, None))))
        }
      }

      "find placeholder in content solely containing a placeholder with index" in {
        forAll(keyGen, indexGen) { (key, index) ⇒
          PlaceholderResolver.findPlaceholders(s"<$key[$index]>") should be(Right(List(Placeholder(key, Some(index)))))
        }
      }

      "find placeholder in content starting with whitespace and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          PlaceholderResolver.findPlaceholders(s" <$key>") should be(Right(List(Placeholder(key, None))))
        }
      }

      "find placeholder in content starting with 2 whitespaces and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          PlaceholderResolver.findPlaceholders(s"  <$key>") should be(Right(List(Placeholder(key, None))))
        }
      }

      "find placeholder in content finishing with whitespace and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          PlaceholderResolver.findPlaceholders(s"<$key> ") should be(Right(List(Placeholder(key, None))))
        }
      }

      "find placeholder in content finishing with 2 whitespaces and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          PlaceholderResolver.findPlaceholders(s"<$key>  ") should be(Right(List(Placeholder(key, None))))
        }
      }

      "find placeholder in random content containing a placeholder with index" in {
        forAll(keyGen, indexGen, Gen.alphaStr) { (key, index, content) ⇒
          PlaceholderResolver.findPlaceholders(s"$content<$key[$index]>$content") should be(Right(List(Placeholder(key, Some(index)))))
        }
      }

      // FIXME '<' is always accepted inside the key, the parser backtracks and consumes it twice??
      "do not accept placeholders containing forbidden char" ignore {
        val genInvalidChar = Gen.oneOf(Session.notAllowedInKey.toList)
        forAll(keyGen, indexGen, Gen.alphaStr, genInvalidChar) { (key, index, content, invalid) ⇒
          PlaceholderResolver.findPlaceholders(s"$content<$invalid$key[$index]>$content") should be(Right(Nil))
        }
      }
    }

    "fillPlaceholders" must {
      "replace a single string" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newEmpty.addValueUnsafe(ph, value)
          val content = s"This project is <$ph>"
          PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Right(s"This project is $value"))
        }
      }

      "not be confused by markup order" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newEmpty.addValueUnsafe(ph, value)
          val content = s"This project is >$ph<"
          PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Right(s"This project is >$ph<"))
        }
      }

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

      "return ResolverError if placeholder not found" in {
        val session = Session.newEmpty.addValueUnsafe("project-name", "cornichon")
        val content = "This project is named <project-new-name>"
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Left(KeyNotFoundInSession("project-new-name", session)))
      }

      "resolve two placeholders" in {
        val session = Session.newEmpty.addValuesUnsafe("project-name" → "cornichon", "taste" → "tasty")
        val content = "This project is named <project-name> and is super <taste>"
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Right("This project is named cornichon and is super tasty"))
      }

      "return ResolverError for the first placeholder not found" in {
        val session = Session.newEmpty.addValuesUnsafe("project-name" → "cornichon", "taste" → "tasty")
        val content = "This project is named <project-name> and is super <new-taste>"
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) should be(Left(KeyNotFoundInSession("new-taste", session)))
      }

      "generate random uuid if <random-uuid> - fixed by seed" in {
        val session = Session.newEmpty
        val content = "<random-uuid>"
        val fixedRc = RandomContext.fromSeed(1L)
        val expectedUUID = "bb1ad573-19b8-9cd8-68fb-0e6f684df992"
        val uuidInSession = PlaceholderResolver.fillPlaceholders(content)(session, fixedRc, noExtractor).valueUnsafe
        UUID.fromString(uuidInSession) should be(UUID.fromString(expectedUUID))
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

      "take the first value in session if index = 0" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
          val content = s"<$key[0]>"
          PlaceholderResolver.fillPlaceholders(content)(s, rc, noExtractor) should be(Right(firstValue))
        }
      }

      "take the second value in session if index = 1" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
          val content = s"<$key[1]>"
          PlaceholderResolver.fillPlaceholders(content)(s, rc, noExtractor) should be(Right(secondValue))
        }
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
