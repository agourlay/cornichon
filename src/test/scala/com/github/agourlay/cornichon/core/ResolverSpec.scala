package com.github.agourlay.cornichon.core

import java.util.UUID

import cats.data.Xor._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ OptionValues, Matchers, WordSpec }
import org.scalacheck.Gen
import com.github.agourlay.cornichon.core.SessionSpec._

class ResolverSpec extends WordSpec with Matchers with OptionValues with PropertyChecks {

  val resolver = Resolver.withoutExtractor()

  "Resolver" when {
    "findPlaceholders" must {
      "find placeholder in content solely containing a placeholder without index" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"<$key>") should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content solely containing a placeholder with index" in {
        forAll(keyGen, indiceGen) { (key, indice) ⇒
          resolver.findPlaceholders(s"<$key[$indice]>") should be(List(Placeholder(key, Some(indice))))
        }
      }

      "find placeholder in content starting with whitespace and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s" <$key>") should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content starting with 2 whitespaces and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"  <$key>") should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content finishing with whitespace and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"<$key> ") should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in content finishing with 2 whitespaces and containing a placeholder" in {
        forAll(keyGen) { key ⇒
          resolver.findPlaceholders(s"<$key>  ") should be(List(Placeholder(key, None)))
        }
      }

      "find placeholder in random content containing a placeholder with index" in {
        forAll(keyGen, indiceGen) { (key, indice) ⇒
          val content1 = Gen.alphaStr
          resolver.findPlaceholders(s"$content1<$key[$indice]>$content1") should be(List(Placeholder(key, Some(indice))))
        }
      }
    }

    "fillPlaceholders" must {
      "replace a single string" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newSession.addValue(ph, value)
          val content = s"This project is <$ph>"
          resolver.fillPlaceholders(content)(session) should be(right(s"This project is $value"))
        }
      }

      "not be confused by markup order" in {
        forAll(keyGen, valueGen) { (ph, value) ⇒
          val session = Session.newSession.addValue(ph, value)
          val content = s"This project is >$ph<"
          resolver.fillPlaceholders(content)(session) should be(right(s"This project is >$ph<"))
        }
      }

      "not be confused if key contains empty string" in {
        val session = Session.newSession.addValue("project-name", "cornichon")
        val content = "This project is named <project name>"
        resolver.fillPlaceholders(content)(session) should be(right("This project is named <project name>"))
      }

      "not be confused by unclosed markup used in a math context" in {
        val session = Session.newSession.addValue("pi", "3.14")
        val content = "3.15 > <pi>"
        resolver.fillPlaceholders(content)(session) should be(right("3.15 > 3.14"))
      }

      "return ResolverError if placeholder not found" in {
        val session = Session.newSession.addValue("project-name", "cornichon")
        val content = "This project is named <project-new-name>"
        resolver.fillPlaceholders(content)(session) should be(left(KeyNotFoundInSession("project-new-name", session)))
      }

      "resolve two placeholders" in {
        val session = Session.newSession.addValues(Seq("project-name" → "cornichon", "taste" → "tasty"))
        val content = "This project is named <project-name> and is super <taste>"
        resolver.fillPlaceholders(content)(session) should be(right("This project is named cornichon and is super tasty"))
      }

      "return ResolverError for the first placeholder not found" in {
        val session = Session.newSession.addValues(Seq("project-name" → "cornichon", "taste" → "tasty"))
        val content = "This project is named <project-name> and is super <new-taste>"
        resolver.fillPlaceholders(content)(session) should be(left(KeyNotFoundInSession("new-taste", session)))
      }

      "generate random uuid if <random-uuid>" in {
        val session = Session.newSession
        val content = "<random-uuid>"
        // throws if invalid UUID
        UUID.fromString(resolver.fillPlaceholders(content)(session).getOrElse(""))
      }

      "take the first value in session if indice = 0" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newSession.addValue(key, firstValue).addValue(key, secondValue)
          val content = s"<$key[0]>"
          resolver.fillPlaceholders(content)(s) should be(right(firstValue))
        }
      }

      "take the second value in session if indice = 1" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newSession.addValue(key, firstValue).addValue(key, secondValue)
          val content = s"<$key[1]>"
          resolver.fillPlaceholders(content)(s) should be(right(secondValue))
        }
      }

      "get value from GenMapper" in {
        val mapper = Map("letter-from-gen" → GenMapper(Gen.oneOf(List("a"))))
        val res = new Resolver(mapper)
        val content = s"<letter-from-gen>"
        res.fillPlaceholders(content)(Session.newSession) should be(right("a"))
      }

      "fail if GenMapper does not return a value" in {
        val mapper = Map("letter-from-gen" → GenMapper(Gen.oneOf(List())))
        val res = new Resolver(mapper)
        val content = s"<letter-from-gen>"
        res.fillPlaceholders(content)(Session.newSession) should be(left(GeneratorError("<letter-from-gen>")))
      }
    }
  }
}
