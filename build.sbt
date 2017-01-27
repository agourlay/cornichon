import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

name := "cornichon"
organization := "com.github.agourlay"

description := "Scala DSL for testing HTTP JSON API"
licenses := Seq("Apache License, ASL Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/agourlay/cornichon"))

scalaVersion := "2.12.1"
crossScalaVersions := Seq("2.11.8", "2.12.1")

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "UTF-8",
  "-Ywarn-dead-code",
  "-Ywarn-unused",
  "-Ywarn-unused-import",
  "-Ywarn-numeric-widen",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-feature",
  "-Xlint:missing-interpolator"
)

fork in Test := true

// To profile tests execution
//javaOptions in Test := Seq("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder")

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(RewriteArrowSymbols, true)

libraryDependencies ++= {
  val scalaTestV = "3.0.1"
  val akkaV = "2.4.16"
  val akkaHttpV = "10.0.1"
  val catsV = "0.8.1"
  val parboiledV = "2.1.4"
  val akkaSseV = "2.0.0"
  val scalaCheckV = "1.13.4"
  val sangriaCirceV = "1.0.0"
  val circeVersion = "0.6.1"
  val diffJsonVersion = "2.1.1"
  val sangriaV = "1.0.0"
  val fansiV = "0.2.3"
  val akkaHttpCirce = "1.11.0"
  val catsScalaTest = "2.1.1"
  val ficusV = "1.4.0"
  Seq(
     "com.typesafe.akka"   %% "akka-actor"      % akkaV
    ,"com.typesafe.akka"   %% "akka-http-core"  % akkaHttpV
    ,"com.typesafe.akka"   %% "akka-http"       % akkaHttpV
    ,"de.heikoseeberger"   %% "akka-sse"        % akkaSseV
    ,"org.typelevel"       %% "cats-macros"     % catsV
    ,"org.typelevel"       %% "cats-core"       % catsV
    ,"org.scalatest"       %% "scalatest"       % scalaTestV
    ,"com.iheart"          %% "ficus"           % ficusV
    ,"org.parboiled"       %% "parboiled"       % parboiledV
    ,"com.lihaoyi"         %% "fansi"           % fansiV
    ,"org.sangria-graphql" %% "sangria"         % sangriaV
    ,"org.sangria-graphql" %% "sangria-circe"   % sangriaCirceV
    ,"io.circe"            %% "circe-core"      % circeVersion
    ,"io.circe"            %% "circe-generic"   % circeVersion
    ,"io.circe"            %% "circe-parser"    % circeVersion
    ,"org.gnieh"           %% "diffson-circe"   % diffJsonVersion
    ,"org.scalacheck"      %% "scalacheck"      % scalaCheckV     % Test
    ,"de.heikoseeberger"   %% "akka-http-circe" % akkaHttpCirce   % Test
    ,"com.ironcorelabs"    %% "cats-scalatest"  % catsScalaTest   % Test
  )
}

// Publishing
releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := (_ => false)
publishTo := Some(
  if (version.value.trim.endsWith("SNAPSHOT"))
    "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

// Additional meta-info required by maven central
startYear := Some(2015)
organizationHomepage := Some(url("https://github.com/agourlay/cornichon"))
developers := Developer("agourlay", "Arnaud Gourlay", "", url("https://github.com/agourlay")) :: Nil
scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/agourlay/cornichon.git"),
  connection = "scm:git:git@github.com:agourlay/cornichon.git"
))

// Integration tests
lazy val root = project.in(file(".")).configs(IntegrationTest)
Defaults.itSettings
