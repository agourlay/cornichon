import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import sbt.Developer
import sbt.Keys.{crossScalaVersions, developers, organizationHomepage, publishMavenStyle, scmInfo, startYear}

lazy val compilerOptions = Seq(
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

lazy val standardSettings = Seq(
  organization := "com.github.agourlay",
  description := "An extensible Scala DSL for testing JSON HTTP APIs.",
  homepage := Some(url("https://github.com/agourlay/cornichon")),
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.8", "2.12.1"),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  fork in Test := true,
  scalacOptions ++= compilerOptions,
  // Additional meta-info required by maven central
  startYear := Some(2015),
  organizationHomepage := Some(url("https://github.com/agourlay/cornichon")),
  developers := Developer("agourlay", "Arnaud Gourlay", "", url("https://github.com/agourlay")) :: Nil,
  scmInfo := Some(ScmInfo(
    browseUrl = url("https://github.com/agourlay/cornichon.git"),
    connection = "scm:git:git@github.com:agourlay/cornichon.git"
  ))
  // To profile tests execution
  //javaOptions in Test := Seq("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder")
)

lazy val publishingSettings = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := (_ => false),
  publishTo := Some(
    if (version.value.trim.endsWith("SNAPSHOT"))
      "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val cornichon =
  project
    .in(file("."))
    .aggregate(core, docs)
    .settings(noPublishSettings)
    .settings(
      unmanagedSourceDirectories.in(Compile) := Seq.empty,
      unmanagedSourceDirectories.in(Test) := Seq.empty
    )

lazy val core =
  project
    .in(file("./cornichon"))
    .enablePlugins(SbtScalariform)
    .configs(IntegrationTest)
    .settings(Defaults.itSettings : _*)
    .settings(standardSettings)
    .settings(publishingSettings)
    .settings(scalariformSettings)
    .settings(
      name := "cornichon",
      libraryDependencies ++= Seq(
        library.akkaActor,
        library.akkaHttp,
        library.akkaHttpCore,
        library.akkaSse,
        library.catsCore,
        library.catsMacro,
        library.scalatest,
        library.ficus,
        library.parboiled,
        library.fansi,
        library.sangria,
        library.sangriaCirce,
        library.circeCore,
        library.circeGeneric,
        library.circeParser,
        library.diffsonCirce,
        library.scalacheck % Test,
        library.akkHttpCirce % Test,
        library.catsScalatest % Test
      )
    )

lazy val docs =
  project
    .in(file("./cornichon-docs"))
    .settings(
      name := "cornichon-docs"
    )
    .dependsOn(core)
    .enablePlugins(MicrositesPlugin)
    .settings(standardSettings)
    .settings(docSettings)
    .settings(unidocSettings)
    .settings(ghpages.settings)
    .settings(noPublishSettings)

lazy val docSettings = Seq(
  micrositeName := "Cornichon",
  micrositeDescription := "An extensible Scala DSL for testing JSON HTTP APIs.",
  micrositeAuthor := "Arnaud Gourlay",
  micrositeHighlightTheme := "atom-one-light",
  micrositeHomepage := "http://agourlay.github.io/cornichon/",
  micrositeBaseUrl := "cornichon",
  micrositeGithubOwner := "agourlay",
  micrositeGithubRepo := "cornichon",
  micrositePalette := Map(
    "brand-primary" -> "#5B5988",
    "brand-secondary" -> "#292E53",
    "brand-tertiary" -> "#222749",
    "gray-dark" -> "#49494B",
    "gray" -> "#7B7B7E",
    "gray-light" -> "#E5E5E6",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF"),
  autoAPIMappings := true,
  micrositeDocumentationUrl := "api",
  addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), micrositeDocumentationUrl),
  ghpagesNoJekyll := false,
  fork in tut := true,
  git.remoteRepo := "git@github.com:agourlay/cornichon.git",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md"
)

lazy val scalariformSettings = SbtScalariform.scalariformSettings ++ Seq(
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(RewriteArrowSymbols, true)
)

lazy val library =
  new {
    object Version {
      val scalaTest     = "3.0.1"
      val akkaActor     = "2.4.17"
      val akkaHttp      = "10.0.3"
      val cats          = "0.9.0"
      val parboiled     = "2.1.4"
      val akkaSse       = "2.0.0"
      val scalaCheck    = "1.13.4"
      val sangriaCirce  = "1.0.1"
      val circe         = "0.7.0"
      val diffson       = "2.1.2"
      val sangria       = "1.0.0"
      val fansi         = "0.2.3"
      val akkaHttpCirce = "1.12.0"
      val catsScalaTest = "2.2.0"
      val ficus         = "1.4.0"
    }
    val akkaActor     = "com.typesafe.akka"   %% "akka-actor"      % Version.akkaActor
    val akkaHttp      = "com.typesafe.akka"   %% "akka-http-core"  % Version.akkaHttp
    val akkaHttpCore  = "com.typesafe.akka"   %% "akka-http"       % Version.akkaHttp
    val akkHttpCirce  = "de.heikoseeberger"   %% "akka-http-circe" % Version.akkaHttpCirce
    val akkaSse       = "de.heikoseeberger"   %% "akka-sse"        % Version.akkaSse
    val catsMacro     = "org.typelevel"       %% "cats-macros"     % Version.cats
    val catsCore      = "org.typelevel"       %% "cats-core"       % Version.cats
    val scalatest     = "org.scalatest"       %% "scalatest"       % Version.scalaTest
    val ficus         = "com.iheart"          %% "ficus"           % Version.ficus
    val parboiled     = "org.parboiled"       %% "parboiled"       % Version.parboiled
    val fansi         = "com.lihaoyi"         %% "fansi"           % Version.fansi
    val sangria       = "org.sangria-graphql" %% "sangria"         % Version.sangria
    val sangriaCirce  = "org.sangria-graphql" %% "sangria-circe"   % Version.sangriaCirce
    val circeCore     = "io.circe"            %% "circe-core"      % Version.circe
    val circeGeneric  = "io.circe"            %% "circe-generic"   % Version.circe
    val circeParser   = "io.circe"            %% "circe-parser"    % Version.circe
    val diffsonCirce  = "org.gnieh"           %% "diffson-circe"   % Version.diffson
    val scalacheck    = "org.scalacheck"      %% "scalacheck"      % Version.scalaCheck
    val catsScalatest = "com.ironcorelabs"    %% "cats-scalatest"  % Version.catsScalaTest
  }