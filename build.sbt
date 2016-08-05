import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

name := "cornichon"
organization := "com.github.agourlay"

description := "Scala DSL for testing HTTP JSON API"
licenses := Seq("Apache License, ASL Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/agourlay/cornichon"))

scalaVersion := "2.11.8"
scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "UTF-8",
  "-Ywarn-dead-code",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-feature",
  "-Ywarn-unused-import"
)

fork in Test := true

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(RewriteArrowSymbols, true)

libraryDependencies ++= {
  val scalaTestV = "3.0.0"
  val akkaHttpV = "2.4.9-RC2"
  val catsV = "0.6.1"
  val logbackV = "1.1.7"
  val parboiledV = "2.1.3"
  val akkaSseV = "1.8.1"
  val scalaCheckV = "1.13.2"
  val sangriaCirceV = "0.4.4"
  val circeVersion = "0.5.0-M2"
  val sangriaV = "0.7.2"
  val fansiV = "0.1.3"
  val akkaHttpCirce = "1.8.0"
  val catsScalaTest = "1.3.0"
  val ficusV = "1.1.2"
  Seq(
     "com.typesafe.akka"   %% "akka-http-core"         % akkaHttpV
    ,"de.heikoseeberger"   %% "akka-sse"               % akkaSseV
    ,"org.typelevel"       %% "cats-macros"            % catsV
    ,"org.typelevel"       %% "cats-core"              % catsV
    ,"org.scalatest"       %% "scalatest"              % scalaTestV
    ,"net.ceedubs"         %% "ficus"                  % ficusV
    ,"ch.qos.logback"      %  "logback-classic"        % logbackV
    ,"org.parboiled"       %% "parboiled"              % parboiledV
    ,"org.scalacheck"      %% "scalacheck"             % scalaCheckV
    ,"com.lihaoyi"         %% "fansi"                  % fansiV
    ,"org.sangria-graphql" %% "sangria"                % sangriaV
    ,"org.sangria-graphql" %% "sangria-circe"          % sangriaCirceV
    ,"io.circe"            %% "circe-core"             % circeVersion
    ,"io.circe"            %% "circe-generic"          % circeVersion
    ,"io.circe"            %% "circe-parser"           % circeVersion
    //,"io.circe"            %% "circe-optics"           % circeVersion  Remove if cursors are used instead or lenses for JsonPath.
    ,"de.heikoseeberger"   %% "akka-http-circe"        % akkaHttpCirce   % "test"
    ,"com.typesafe.akka"   %% "akka-http-experimental" % akkaHttpV       % "test"
    ,"com.ironcorelabs"    %% "cats-scalatest"         % catsScalaTest   % "test"
  )
}

// Wartremover
wartremoverErrors in (Compile, compile) ++= Seq(
  Wart.Any2StringAdd, Wart.Option2Iterable, Wart.NoNeedForMonad,
  Wart.Return, Wart.TryPartial)

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