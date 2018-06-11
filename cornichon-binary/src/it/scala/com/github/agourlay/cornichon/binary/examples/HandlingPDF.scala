package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature
import main.scala.com.github.agourlay.cornichon.binary.BinaryDSL

class HandlingPDF extends CornichonFeature with BinaryDSL{
  def feature = Feature("PDF") {

    Scenario("can handle PDF") {

       When I get("http://www.inkwelleditorial.com/pdfSample.pdf")

       Then assert status.is(200)

       Then assert binaryBody.asPDF.hasPages(2)

    }
  }
}
