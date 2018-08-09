package com.github.agourlay.cornichon.resolver

import java.util.UUID

import cats.scalatest.{ EitherMatchers, EitherValues }
import com.github.agourlay.cornichon.core.SessionSpec._
import com.github.agourlay.cornichon.core.{ KeyNotFoundInSession, Session }
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, OptionValues, WordSpec }

class PlaceholderResolverSpec extends WordSpec
  with Matchers
  with OptionValues
  with PropertyChecks
  with EitherValues
  with EitherMatchers {

  private val resolver = PlaceholderResolver.withoutExtractor()

  "Resolver" when {
    "findPlaceholders" must {
      "find placeholder in content solely containing a placeholder without index" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"<$key>").value should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content solely containing a placeholder with index" in {
        forAll(keyGen, indiceGen) { (key, indice) ⇒
          resolver.findPlaceholders(s"<$key[$indice]>").value should be(List(Placeholder(key, Some(indice))))
        }
      }

      "find placeholder in content starting with whitespace and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s" <$key>").value should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content starting with 2 whitespaces and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"  <$key>").value should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content finishing with whitespace and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"<$key> ").value should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content finishing with 2 whitespaces and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"<$key>  ").value should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in random content containing a placeholder with index" in {
        forAll(keyGen, indiceGen, Gen.alphaStr) { (key, indice, content) ⇒
          resolver.findPlaceholders(s"$content<$key[$indice]>$content").value should be(List(Placeholder(key, Some(indice))))
        }
      }

      // FIXME for some reason '<' is always accepted inside the key, maybe the parser backtracks and consum it twice??
      "do not accept placeholders containing forbidden char" ignore {
        val genInvalidChar = Gen.oneOf(Session.notAllowedInKey.toList)
        forAll(keyGen, indiceGen, Gen.alphaStr, genInvalidChar) { (key, indice, content, invalid) ⇒
          resolver.findPlaceholders(s"$content<$invalid$key[$indice]>$content").value should be(Nil)
        }
      }
    }

    "fillPlaceholders" must {
      "replace a single string" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newEmpty.addValueUnsafe(ph, value)
          val content = s"This project is <$ph>"
          resolver.fillPlaceholders(content)(session).value should be(s"This project is $value")
        }
      }

      "not be confused by markup order" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newEmpty.addValueUnsafe(ph, value)
          val content = s"This project is >$ph<"
          resolver.fillPlaceholders(content)(session).value should be(s"This project is >$ph<")
        }
      }

      "not be confused if key contains empty string" in {
        val session = Session.newEmpty.addValueUnsafe("project-name", "cornichon")
        val content = "This project is named <project name>"
        resolver.fillPlaceholders(content)(session).value should be("This project is named <project name>")
      }

      "not be confused by unclosed markup used in a math context" in {
        val session = Session.newEmpty.addValueUnsafe("pi", "3.14")
        val content = "3.15 > <pi>"
        resolver.fillPlaceholders(content)(session).value should be("3.15 > 3.14")
      }

      "not be confused by markup language" in {
        val session = Session.newEmpty.addValueUnsafe("pi", "3.14")
        val content = "PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT <pi>"
        resolver.fillPlaceholders(content)(session).value should be("PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT 3.14")
      }

      "return ResolverError if placeholder not found" in {
        val session = Session.newEmpty.addValueUnsafe("project-name", "cornichon")
        val content = "This project is named <project-new-name>"
        resolver.fillPlaceholders(content)(session).leftValue should be(KeyNotFoundInSession("project-new-name", session))
      }

      "resolve two placeholders" in {
        val session = Session.newEmpty.addValuesUnsafe("project-name" → "cornichon", "taste" → "tasty")
        val content = "This project is named <project-name> and is super <taste>"
        resolver.fillPlaceholders(content)(session).value should be("This project is named cornichon and is super tasty")
      }

      "return ResolverError for the first placeholder not found" in {
        val session = Session.newEmpty.addValuesUnsafe("project-name" → "cornichon", "taste" → "tasty")
        val content = "This project is named <project-name> and is super <new-taste>"
        resolver.fillPlaceholders(content)(session).leftValue should be(KeyNotFoundInSession("new-taste", session))
      }

      "generate random uuid if <random-uuid>" in {
        val session = Session.newEmpty
        val content = "<random-uuid>"
        // throws if invalid UUID
        UUID.fromString(resolver.fillPlaceholders(content)(session).getOrElse(""))
      }

      "take the first value in session if indice = 0" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
          val content = s"<$key[0]>"
          resolver.fillPlaceholders(content)(s).value should be(firstValue)
        }
      }

      "take the second value in session if indice = 1" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
          val content = s"<$key[1]>"
          resolver.fillPlaceholders(content)(s).value should be(secondValue)
        }
      }

      "fail with clear error message if key is defined in both Session and Extractors" in {
        val extractor = JsonMapper("customer", "id")
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty.addValueUnsafe("customer-id", "12345")
        resolverWithExt.fillPlaceholders("<customer-id>")(s).leftValue should be(AmbiguousKeyDefinition("customer-id"))
      }

      "fail with clear error message if key defined in the extractor is not in Session" in {
        val extractor = JsonMapper("customer", "id")
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty
        resolverWithExt.fillPlaceholders("<customer-id>")(s).leftValue.renderedMessage should be("Error occurred while running Mapper attached to key 'customer-id'\ncaused by:\nkey 'customer' can not be found in session \nempty")
      }

      "use registered SimpleMapper" in {
        val extractor = SimpleMapper(() ⇒ "magic!")
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty
        resolverWithExt.fillPlaceholders("<customer-id>")(s).value should be("magic!")
      }

      "use registered TextMapper" in {
        val extractor = TextMapper("customer", customerString ⇒ customerString.length.toString)
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty.addValueUnsafe("customer", "my-customer-name-of-great-length")
        resolverWithExt.fillPlaceholders("<customer-id>")(s).value should be("32")
      }

      "use registered HistoryMapper" in {
        val extractor = HistoryMapper("customer", customers ⇒ customers.length.toString)
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty.addValueUnsafe("customer", "customer1")
          .addValueUnsafe("customer", "customer2")
          .addValueUnsafe("customer", "customer3")
        resolverWithExt.fillPlaceholders("<customer-id>")(s).value should be("3")
      }

      "use registered SessionMapper" in {
        val extractor = SessionMapper(s ⇒ s.get("other-thing"))
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty.addValueUnsafe("other-thing", "other unrelated value")
        resolverWithExt.fillPlaceholders("<customer-id>")(s).value should be("other unrelated value")
      }

      "use registered RandomMapper" in {
        val extractor = RandomMapper(rd ⇒ rd.alphanumeric.take(5).mkString("").length.toString)
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty
        resolverWithExt.fillPlaceholders("<customer-id>")(s).value should be("5")
      }

      "use registered JsonMapper" in {
        val extractor = JsonMapper("customer", "id")
        val resolverWithExt = new PlaceholderResolver(Map("customer-id" → extractor))
        val s = Session.newEmpty.addValueUnsafe("customer", """{"id" : "122"}""")
        resolverWithExt.fillPlaceholders("<customer-id>")(s).value should be("122")
      }
    }
  }
}
