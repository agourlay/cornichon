package com.github.agourlay.cornichon.framework

import cats.syntax.either._

import java.io.{ File, PrintWriter }
import java.net.{ InetAddress, UnknownHostException }
import java.text.SimpleDateFormat
import java.util.Properties

import sbt.testing.{ Event, Status }

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration

// Freely adapted from Scalatest's "org.scalatest.tools.JUnitXmlReporter"
object JUnitXmlReporter {

  private lazy val propertiesXml = genPropertiesXml

  private lazy val hostname: String =
    try {
      InetAddress.getLocalHost.getHostName
    } catch {
      case _: UnknownHostException => "unknown hostname"
    }

  //
  // Generates <properties> element of xml.
  //
  private def genPropertiesXml: xml.Elem = {
    val sysprops = System.getProperties

    <properties>
      {
        for (name <- propertyNames(sysprops))
          yield <property name={ name } value={ sysprops.getProperty(name) }/>
      }
    </properties>
  }

  //
  // Returns a list of the names of properties in a Properties object.
  //
  private def propertyNames(props: Properties): List[String] = {
    val listBuf = new ListBuffer[String]
    val enumeration = props.propertyNames
    while (enumeration.hasMoreElements)
      listBuf += "" + enumeration.nextElement

    listBuf.toList
  }

  private case class TestSuite(name: String, timeStamp: Long, duration: FiniteDuration, events: List[Event]) {
    val errors: Int = events.count(_.status() == Status.Error)
    val failures: Int = events.count(_.status() == Status.Failure)
    val testCases: List[TestCase] = events.map { e =>
      val tc = TestCase(e.fullyQualifiedName(), e.duration())
      e.status() match {
        case Status.Canceled => tc.copy(canceled = true)
        case Status.Failure  => tc.copy(failure = Option.apply(e.throwable().get()))
        case Status.Ignored  => tc.copy(ignored = true)
        case Status.Pending  => tc.copy(pending = true)
        case _               => tc
      }
    }
  }

  private case class TestCase(name: String, duration: Long, pending: Boolean = false, canceled: Boolean = false, ignored: Boolean = false, failure: Option[Throwable] = None)

  def checkReportsFolder(reportsOutputDir: String): Unit = {
    val outputDir: File = new File(reportsOutputDir)
    if (outputDir.exists()) {
      println(s"$reportsOutputDir already exists - will use it")
    } else {
      val created = outputDir.mkdirs()
      if (created) {
        println(s"$reportsOutputDir did not exist so it was created")
      } else {
        println(s"ERROR: $reportsOutputDir did not exist and could not be created")
        sys.exit(1)
      }
    }
  }

  def writeJunitReport(directory: String, featureName: String, duration: FiniteDuration, startingTimestamp: Long, events: List[Event]): Either[Throwable, Unit] =
    Either.catchNonFatal {
      val testSuite = TestSuite(featureName, startingTimestamp, duration, events)
      val xmlStr = renderXML(testSuite)

      val reportFile = new File(directory + "/TEST-" + featureName + ".xml")
      reportFile.createNewFile()
      val out = new PrintWriter(reportFile, "UTF-8")

      out.print(xmlStr)
      out.close()
    }

  private def formatTimeStamp(timeStamp: Long): String = {
    val dateFmt = new SimpleDateFormat("yyyy-MM-dd")
    val timeFmt = new SimpleDateFormat("HH:mm:ss")
    dateFmt.format(timeStamp) + "T" + timeFmt.format(timeStamp)
  }

  def getStackTrace(throwable: Throwable): String = {
    "" + throwable +
      Array.concat(throwable.getStackTrace).mkString(
        "\n      at ",
        "\n      at ", "\n") +
        {
          if (throwable.getCause != null) {
            "      Cause: " +
              getStackTrace(throwable.getCause)
          } else ""
        }
  }

  private def failureXml(failureOption: Option[Throwable]): xml.NodeSeq = {
    failureOption match {
      case None =>
        xml.NodeSeq.Empty

      case Some(throwable) =>
        val (throwableType, throwableText) = {
          val throwableType = "" + throwable.getClass
          val throwableText = getStackTrace(throwable)
          (throwableType, throwableText)
        }

        <failure type={ throwableType }> { throwableText } </failure>
    }
  }

  def renderXML(testSuite: TestSuite): String = {
    val xmlVal =
      <testsuite errors={ "" + testSuite.errors } failures={ "" + testSuite.failures } hostname={ "" + hostname } name={ "" + testSuite.name } tests={ "" + testSuite.testCases.size } duration={ "" + testSuite.duration } timestamp={ "" + formatTimeStamp(testSuite.timeStamp) }>
        { propertiesXml }
        {
          for (testCase <- testSuite.testCases) yield {
            <testcase name={ "" + testCase.name } duration={ "" + testCase.duration }>
              {
                if (testCase.ignored || testCase.pending || testCase.canceled)
                  <skipped/>
                else
                  failureXml(testCase.failure)
              }
            </testcase>
          }
        }
        <system-out><![CDATA[]]></system-out>
        <system-err><![CDATA[]]></system-err>
      </testsuite>

    val prettified = new xml.PrettyPrinter(76, 2).format(xmlVal)

    // scala xml strips out the <![CDATA[]]> elements, so restore them here
    val withCDATA =
      prettified.replace(
        "<system-out></system-out>",
        "<system-out><![CDATA[]]></system-out>").
        replace(
          "<system-err></system-err>",
          "<system-err><![CDATA[]]></system-err>")

    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" + withCDATA
  }
}