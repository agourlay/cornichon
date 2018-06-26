package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature
import main.scala.com.github.agourlay.cornichon.binary.BinaryDSL

class HandlingPDF extends CornichonFeature with BinaryDSL{
  def feature = Feature("PDF") {

    Scenario("can handle PDF") {

       When I get("http://www.inkwelleditorial.com/pdfSample.pdf")

       Then assert status.is(200)

       Then assert binaryBody.as(PDF).hasPages(2)

       Then assert binaryBody.asPDF.hasPages(2)

       Then assert binaryBody.asPDF.contains("The following e-books help you begin your quest as an entrepreneur")

       Then assert binaryBody.asPDF.pageContains(1, "The following e-books help you begin your quest as an entrepreneur")
    }
  }
}

/*exception thrown java.io.IOException: java.util.zip.DataFormatException: invalid code lengths set
at org.apache.pdfbox.filter.FlateFilter.decode(FlateFilter.java:83)
at org.apache.pdfbox.filter.Filter.decode(Filter.java:87)
at org.apache.pdfbox.cos.COSInputStream.create(COSInputStream.java:77)
at org.apache.pdfbox.cos.COSStream.createInputStream(COSStream.java:175)
at org.apache.pdfbox.cos.COSStream.createInputStream(COSStream.java:163)
at org.apache.pdfbox.pdmodel.PDPage.getContents(PDPage.java:157)
at org.apache.pdfbox.pdfparser.PDFStreamParser.<init>(PDFStreamParser.java:91)
at org.apache.pdfbox.contentstream.PDFStreamEngine.processStreamOperators(PDFStreamEngine.java:493)
at org.apache.pdfbox.contentstream.PDFStreamEngine.processStream(PDFStreamEngine.java:477)
at org.apache.pdfbox.contentstream.PDFStreamEngine.processPage(PDFStreamEngine.java:150)
at org.apache.pdfbox.text.LegacyPDFStreamEngine.processPage(LegacyPDFStreamEngine.java:139)
at org.apache.pdfbox.text.PDFTextStripper.processPage(PDFTextStripper.java:391)
at org.apache.pdfbox.text.PDFTextStripper.processPages(PDFTextStripper.java:319)
at org.apache.pdfbox.text.PDFTextStripper.writeText(PDFTextStripper.java:266)
at org.apache.pdfbox.text.PDFTextStripper.getText(PDFTextStripper.java:227)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.$anonfun$readPages$2(PdfStepBuilder.scala:34)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.$anonfun$readPages$2$adapted(PdfStepBuilder.scala:32)
at scala.collection.TraversableLike.$anonfun$map$1(TraversableLike.scala:234)
at scala.collection.immutable.Range.foreach(Range.scala:156)
at scala.collection.TraversableLike.map(TraversableLike.scala:234)
at scala.collection.TraversableLike.map$(TraversableLike.scala:227)
at scala.collection.AbstractTraversable.map(Traversable.scala:104)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.$anonfun$readPages$1(PdfStepBuilder.scala:32)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.autoClose(PdfStepBuilder.scala:26)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.readPages(PdfStepBuilder.scala:31)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.$anonfun$contains$3(PdfStepBuilder.scala:59)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.autoClose(PdfStepBuilder.scala:26)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.$anonfun$contains$2(PdfStepBuilder.scala:59)
at scala.util.Either.map(Either.scala:350)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.$anonfun$contains$1(PdfStepBuilder.scala:58)
at com.github.agourlay.cornichon.binary.PdfStepBuilder$.$anonfun$contains$1$adapted(PdfStepBuilder.scala:57)
at com.github.agourlay.cornichon.steps.regular.assertStep.AssertStep.run(AssertStep.scala:19)
at com.github.agourlay.cornichon.core.ValueStep.run(Step.scala:33)
at com.github.agourlay.cornichon.core.ValueStep.run$(Step.scala:31)
at com.github.agourlay.cornichon.steps.regular.assertStep.AssertStep.run(AssertStep.scala:14)
at com.github.agourlay.cornichon.core.Engine.$anonfun$runStep$1(Engine.scala:110)
at cats.syntax.EitherObjectOps$.catchNonFatal$extension(either.scala:308)
at com.github.agourlay.cornichon.core.Engine.runStep(Engine.scala:110)
at com.github.agourlay.cornichon.core.Engine.$anonfun$prepareAndRunStep$4(Engine.scala:105)
at scala.util.Either.fold(Either.scala:188)
at com.github.agourlay.cornichon.core.Engine.prepareAndRunStep(Engine.scala:105)
at com.github.agourlay.cornichon.core.Engine.$anonfun$runSteps$2(Engine.scala:71)
at monix.eval.internal.TaskRunLoop$.startFull(TaskRunLoop.scala:121)
at monix.eval.internal.TaskRunLoop$RestartCallback.onSuccess(TaskRunLoop.scala:537)
at monix.eval.Callback.apply(Callback.scala:48)
at monix.eval.Callback.apply(Callback.scala:38)
at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
at monix.execution.schedulers.TrampolineExecutionContext.monix$execution$schedulers$TrampolineExecutionContext$$localRunLoop(TrampolineExecutionContext.scala:109)
at monix.execution.schedulers.TrampolineExecutionContext.$anonfun$startLoopNormal$1(TrampolineExecutionContext.scala:103)
at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:12)
at scala.concurrent.BlockContext$.withBlockContext(BlockContext.scala:81)
at monix.execution.schedulers.TrampolineExecutionContext.startLoopNormal(TrampolineExecutionContext.scala:103)
at monix.execution.schedulers.TrampolineExecutionContext.execute(TrampolineExecutionContext.scala:80)
at scala.concurrent.impl.CallbackRunnable.executeWithValue(Promise.scala:68)
at scala.concurrent.impl.Promise$DefaultPromise.$anonfun$tryComplete$1(Promise.scala:284)
at scala.concurrent.impl.Promise$DefaultPromise.$anonfun$tryComplete$1$adapted(Promise.scala:284)
at scala.concurrent.impl.Promise$DefaultPromise.tryComplete(Promise.scala:284)
at scala.concurrent.Promise.complete(Promise.scala:49)
at scala.concurrent.Promise.complete$(Promise.scala:48)
at scala.concurrent.impl.Promise$DefaultPromise.complete(Promise.scala:183)
at scala.concurrent.impl.Promise.$anonfun$transform$1(Promise.scala:29)
at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
at java.util.concurrent.ForkJoinTask$RunnableExecuteAction.exec(ForkJoinTask.java:1402)
at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)
Caused by: java.util.zip.DataFormatException: invalid code lengths set
at java.util.zip.Inflater.inflateBytes(Native Method)
at java.util.zip.Inflater.inflate(Inflater.java:259)
at java.util.zip.Inflater.inflate(Inflater.java:280)
at org.apache.pdfbox.filter.FlateFilter.decompress(FlateFilter.java:108)
at org.apache.pdfbox.filter.FlateFilter.decode(FlateFilter.java:74)
... 66 more*/
