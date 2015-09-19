import scalariform.formatter.preferences._

name := "cornichon"

organization := "com.github.agourlay"

version := "0.1.SNAPSHOT"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalaVersion := "2.11.7"

scalacOptions := Seq(
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

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(RewriteArrowSymbols, true)

libraryDependencies ++= {
  val scalaTestV = "2.2.5"
  val akkaHttpV = "1.0"
  val akkaV = "2.3.14"
  val catsV = "0.2.0"
  val sprayJsonV = "1.3.2"
  val json4sV = "3.3.0.RC5"
  val logbackV = "1.1.3"
  val parboiledV = "2.1.0"
  val akkaSseV = "1.1.0"
  val schemaValidatorV = "2.2.6"
  Seq(
     "com.typesafe.akka" %% "akka-http-experimental"            % akkaHttpV
    ,"com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaHttpV
    ,"com.typesafe.akka" %% "akka-slf4j"                        % akkaV
    ,"de.heikoseeberger" %% "akka-sse"                          % akkaSseV
    ,"org.json4s"        %% "json4s-native"                     % json4sV
    ,"io.spray"          %% "spray-json"                        % sprayJsonV
    ,"org.spire-math"    %% "cats-macros"                       % catsV
    ,"org.spire-math"    %% "cats-core"                         % catsV
    ,"com.github.fge"    %  "json-schema-validator"             % schemaValidatorV
    ,"org.scalatest"     %% "scalatest"                         % scalaTestV
    ,"ch.qos.logback"    %  "logback-classic"                   % logbackV
    ,"org.parboiled"     %% "parboiled"                         % parboiledV
  )
}
