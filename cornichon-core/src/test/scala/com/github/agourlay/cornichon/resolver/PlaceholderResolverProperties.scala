package com.github.agourlay.cornichon.resolver

import java.util.UUID

import com.github.agourlay.cornichon.core.{ KeyNotFoundInSession, RandomContext, Session }
import com.github.agourlay.cornichon.core.SessionProperties._
import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop._
import org.typelevel.claimant.Claim

class PlaceholderResolverProperties extends Properties("PlaceholderResolver") {

  private val rc = RandomContext.fromSeed(1L)
  private val noExtractor = Map.empty[String, Mapper]

  property("findPlaceholders finds placeholder in content solely containing a placeholder without index") =
    forAll(keyGen) { key ⇒
      Claim {
        PlaceholderResolver.findPlaceholders(s"<$key>") == Right(List(Placeholder(key, None)))
      }
    }

  property("findPlaceholders find placeholder in content solely containing a placeholder with index") =
    forAll(keyGen, indexGen) { (key, index) ⇒
      Claim {
        PlaceholderResolver.findPlaceholders(s"<$key[$index]>") == Right(List(Placeholder(key, Some(index))))
      }
    }

  property("findPlaceholders find placeholder in content starting with whitespace and containing a placeholder") =
    forAll(keyGen) { key ⇒
      Claim {
        PlaceholderResolver.findPlaceholders(s" <$key>") == Right(List(Placeholder(key, None)))
      }
    }

  property("findPlaceholders find placeholder in content starting with 2 whitespaces and containing a placeholder") =
    forAll(keyGen) { key ⇒
      Claim {
        PlaceholderResolver.findPlaceholders(s"  <$key>") == Right(List(Placeholder(key, None)))
      }
    }

  property("findPlaceholders find placeholder in content finishing with whitespace and containing a placeholder") =
    forAll(keyGen) { key ⇒
      Claim {
        PlaceholderResolver.findPlaceholders(s"<$key> ") == Right(List(Placeholder(key, None)))
      }
    }

  property("findPlaceholders find placeholder in content finishing with 2 whitespaces and containing a placeholder") =
    forAll(keyGen) { key ⇒
      Claim {
        PlaceholderResolver.findPlaceholders(s"<$key>  ") == Right(List(Placeholder(key, None)))
      }
    }

  property("findPlaceholders find placeholder in random content containing a placeholder with index") =
    forAll(keyGen, indexGen, Gen.alphaStr) { (key, index, content) ⇒
      Claim {
        PlaceholderResolver.findPlaceholders(s"$content<$key[$index]>$content") == Right(List(Placeholder(key, Some(index))))
      }
    }

  // FIXME '<' is always accepted inside the key, the parser backtracks and consumes it twice??
  //property("findPlaceholders do not accept placeholders containing forbidden char") = {
  //  val genInvalidChar = Gen.oneOf(Session.notAllowedInKey.toList)
  //  forAll(keyGen, indexGen, Gen.alphaStr, genInvalidChar) { (key, index, content, invalid) ⇒
  //    Claim {
  //      PlaceholderResolver.findPlaceholders(s"$content<$invalid$key[$index]>$content") == Right(Nil)
  //    }
  //  }
  //}

  property("fillPlaceholders replaces a single string") =
    forAll(keyGen, valueGen) { (ph, value) ⇒
      val session = Session.newEmpty.addValueUnsafe(ph, value)
      val content = s"This project is <$ph>"
      Claim {
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) == Right(s"This project is $value")
      }
    }

  property("fillPlaceholders take the first value in session if index = 0") =
    forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
      val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
      val content = s"<$key[0]>"
      Claim {
        PlaceholderResolver.fillPlaceholders(content)(s, rc, noExtractor) == Right(firstValue)
      }
    }

  property("fillPlaceholders take the second value in session if index = 1") =
    forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
      val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
      val content = s"<$key[1]>"
      Claim {
        PlaceholderResolver.fillPlaceholders(content)(s, rc, noExtractor) == Right(secondValue)
      }
    }

  property("fillPlaceholders returns ResolverError if placeholder not found") =
    forAll(keyGen, valueGen, keyGen) { (key, value, secondKey) ⇒
      val session = Session.newEmpty.addValueUnsafe(key, value)
      val content = s"This project is named <$secondKey>"
      Claim {
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) == Left(KeyNotFoundInSession(secondKey, session))
      }
    }

  property("fillPlaceholders resolves two placeholders") =
    forAll(keyGen, valueGen, keyGen, valueGen) { (k1, v1, k2, v2) ⇒
      val session = Session.newEmpty.addValuesUnsafe(k1 → v1, k2 → v2)
      val content = s"This project is named <$k1> and is super <$k2>"
      Claim {
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) == Right(s"This project is named $v1 and is super $v2")
      }
    }

  property("fillPlaceholders returns ResolverError for the first placeholder not found") =
    forAll(keyGen, valueGen, keyGen, valueGen) { (k1, v1, k2, v2) ⇒
      val session = Session.newEmpty.addValuesUnsafe(k1 → v1, k2 → v2)
      val content = s"This project is named <$k1> and is super <other-key>"
      Claim {
        PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) == Left(KeyNotFoundInSession("other-key", session))
      }
    }

  property("generate random uuid if <random-uuid> - fixed by seed") =
    forAll { seed: Long ⇒
      val session = Session.newEmpty
      val content = "<random-uuid>"
      val first = PlaceholderResolver.fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = PlaceholderResolver.fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      Claim {
        UUID.fromString(second) == UUID.fromString(first)
      }
    }
}
