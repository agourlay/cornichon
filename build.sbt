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
  val scalaTestV = "2.2.6"
  val akkaHttpV = "2.4.3"
  val catsV = "0.4.1"
  val sprayJsonV = "1.3.2"
  val json4sV = "3.3.0"
  val logbackV = "1.1.7"
  val parboiledV = "2.1.2"
  val akkaSseV = "1.7.2"
  val scalacheckV = "1.12.5"
  val sangriaV = "0.6.0"
  val sangriaJsonV = "0.3.0"
  Seq(
     "com.typesafe.akka"   %% "akka-http-core"                    % akkaHttpV
    ,"de.heikoseeberger"   %% "akka-sse"                          % akkaSseV
    ,"org.json4s"          %% "json4s-jackson"                    % json4sV
    ,"io.spray"            %% "spray-json"                        % sprayJsonV
    ,"org.typelevel"       %% "cats-macros"                       % catsV
    ,"org.typelevel"       %% "cats-core"                         % catsV
    ,"org.scalatest"       %% "scalatest"                         % scalaTestV
    ,"ch.qos.logback"      %  "logback-classic"                   % logbackV
    ,"org.parboiled"       %% "parboiled"                         % parboiledV
    ,"org.scalacheck"      %% "scalacheck"                        % scalacheckV
    ,"org.sangria-graphql" %% "sangria"                           % sangriaV
    ,"org.sangria-graphql" %% "sangria-json4s-jackson"            % sangriaJsonV
    ,"com.typesafe.akka"   %% "akka-http-spray-json-experimental" % akkaHttpV   % "test"
    ,"com.typesafe.akka"   %% "akka-http-experimental"            % akkaHttpV   % "test"
  )
}

// Wartremover
wartremoverErrors in (Compile, compile) ++= Seq(
  Wart.Any2StringAdd, Wart.Option2Iterable, Wart.OptionPartial,
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