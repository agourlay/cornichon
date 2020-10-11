package com.github.agourlay.cornichon.util

import com.github.agourlay.cornichon.core.CornichonError
import javax.xml.parsers.SAXParserFactory

import scala.xml.{ Elem, InputSource, XML }

object StringUtils {

  //https://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Levenshtein_distance#Scala
  def levenshtein(str1: String, str2: String): Int = {
    def min(nums: Int*): Int = nums.min

    val lenStr1 = str1.length
    val lenStr2 = str2.length

    val d: Array[Array[Int]] = Array.ofDim(lenStr1 + 1, lenStr2 + 1)

    for (i <- 0 to lenStr1) d(i)(0) = i
    for (j <- 0 to lenStr2) d(0)(j) = j

    for (i <- 1 to lenStr1; j <- 1 to lenStr2) {
      val cost = if (str1(i - 1) == str2(j - 1)) 0 else 1

      d(i)(j) = min(
        d(i - 1)(j) + 1, // deletion
        d(i)(j - 1) + 1, // insertion
        d(i - 1)(j - 1) + cost // substitution
      )
    }

    d(lenStr1)(lenStr2)
  }

  // from http4s
  private val saxFactory = {
    val factory = SAXParserFactory.newInstance
    // Safer parsing settings to avoid certain class of XML attacks
    // See https://github.com/scala/scala-xml/issues/17
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setXIncludeAware(false)
    factory
  }

  def parseXml(xmlStr: String): Either[CornichonError, Elem] =
    CornichonError.catchThrowable {
      val is = new InputSource(xmlStr)
      val saxParser = saxFactory.newSAXParser()
      XML.loadXML(is, saxParser)
    }

}
