package com.github.agourlay.cornichon.binary

import com.github.agourlay.cornichon.core.{ CornichonError, Session }
import com.github.agourlay.cornichon.http.HttpService
import com.github.agourlay.cornichon.steps.regular.assertStep._
import cats.instances.int._
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import scala.util.matching.Regex

trait PdfStepBuilder {
  def hasPages(expectedPageNumber: Int): AssertStep
}

object PdfStepBuilder extends PdfStepBuilder {

  private class TextStripper(page: Int) extends PDFTextStripper {
    setStartPage(page)
    setEndPage(page)
  }

  private def autoClose[A](doc: PDDocument)(pdfReader: PDDocument ⇒ A): A = {
    try pdfReader(doc)
    finally doc.close()
  }

  private def readPages(doc: PDDocument): Seq[String] = {
    autoClose(doc) { document ⇒
      (0 until document.getNumberOfPages).map(i ⇒ {
        new TextStripper(page = i + 1)
          .getText(document)
          .replace("\n", " ")
      })
    }
  }

  private def makePdfFromSession(s: Session): Either[CornichonError, PDDocument] =
    for {
      bodyS ← s.get(HttpService.SessionKeys.lastResponseBodyKey)
      pdf ← CornichonError.catchThrowable(PDDocument.load(bodyS.getBytes))
    } yield pdf

  def hasPages(expectedPageNumber: Int) = AssertStep(
    title = s"body is a PDF with '$expectedPageNumber' pages",
    action = s ⇒ Assertion.either {
      makePdfFromSession(s).map { pdf ⇒
        val pageNumber = autoClose(pdf)(_.getNumberOfPages)
        GenericEqualityAssertion(pageNumber, expectedPageNumber)
      }
    })

  def contains(txt: String): AssertStep = AssertStep(
    title = s"body is a PDF with a content matching '$txt'",
    action = s ⇒ Assertion.either {
      makePdfFromSession(s).map { pdf ⇒
        val pages = autoClose(pdf)(readPages)
        StringContainsAssertion(pages.mkString(""), txt)
      }
    })

  def pageContains(pageNum: Int, txt: String): AssertStep = AssertStep(
    title = s"body is a PDF with page '$pageNum' content matching '$txt'",
    action = s ⇒ Assertion.either {
      makePdfFromSession(s).map { pdf ⇒
        val pages = autoClose(pdf)(readPages)
        StringContainsAssertion(pages(pageNum), txt)
      }
    })

  def contentMatchesRex(regex: Regex): AssertStep = AssertStep(
    title = s"body is a PDF with a content matching' $regex'",
    action = s ⇒ Assertion.either {
      makePdfFromSession(s).map { pdf ⇒
        val pages = autoClose(pdf)(readPages)
        RegexAssertion(pages.mkString(""), regex)
      }
    })
}

