package com.github.agourlay.cornichon.binary

import com.github.agourlay.cornichon.core.{ CornichonError, Session }
import com.github.agourlay.cornichon.http.HttpService
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }

import cats.instances.int._

import org.apache.pdfbox.pdmodel.PDDocument

trait PdfStepBuilder {
  def hasPages(expectedPageNumber: Int): AssertStep
}

object PdfStepBuilder extends PdfStepBuilder {

  private def makePdfFromSession(s: Session): Either[CornichonError, PDDocument] =
    for {
      bodyS ← s.get(HttpService.SessionKeys.lastResponseBodyKey)
      pdf ← CornichonError.catchThrowable(PDDocument.load(bodyS.getBytes))
    } yield pdf

  def hasPages(expectedPageNumber: Int) = AssertStep(
    title = s"body is a PDF with '$expectedPageNumber' pages",
    action = s ⇒ Assertion.either {
      makePdfFromSession(s).map { pdf ⇒
        GenericEqualityAssertion(pdf.getNumberOfPages, expectedPageNumber)
      }
    })
}

