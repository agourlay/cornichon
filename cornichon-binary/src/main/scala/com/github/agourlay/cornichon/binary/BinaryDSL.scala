package main.scala.com.github.agourlay.cornichon.binary

import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.binary.PdfStepBuilder

trait BinaryDSL { this: BaseFeature ⇒

  object PDF extends DslBinaryContent[PdfStepBuilder] {
    val builder: PdfStepBuilder = PdfStepBuilder
  }

  trait DslBinaryContent[STEPBUILDER] {
    val builder: STEPBUILDER
  }

  case object BinaryBodyBuilder {
    def as[B](bc: DslBinaryContent[B]): B = bc.builder

    def asPDF: PdfStepBuilder.type = PdfStepBuilder
  }

  def binaryBody: BinaryBodyBuilder.type = BinaryBodyBuilder

}
