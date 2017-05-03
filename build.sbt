import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
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
  scalaVersion := "2.12.2",
  crossScalaVersions := Seq("2.11.11", "2.12.2"),
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

lazy val commonSettings = standardSettings ++ publishingSettings

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val cornichon =
  project
    .in(file("."))
    .aggregate(core, scalatest, docs, benchmarks)
    .settings(commonSettings)
    .settings(noPublishSettings)
    .settings(
      unmanagedSourceDirectories.in(Compile) := Seq.empty,
      unmanagedSourceDirectories.in(Test) := Seq.empty
    )

lazy val core =
  project
    .in(file("./cornichon-core"))
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(publishingSettings)
    .settings(scalariformSettings)
    .settings(
      name := "cornichon-core",
      libraryDependencies ++= Seq(
        library.akkaActor,
        library.akkaStream,
        library.akkaHttp,
        library.akkaHttpCore,
        library.akkaSse,
        library.catsCore,
        library.catsMacro,
        library.ficus,
        library.parboiled,
        library.fansi,
        library.sangria,
        library.sangriaCirce,
        library.circeCore,
        library.circeGeneric,
        library.circeParser,
        library.diffsonCirce,
        library.monixExec,
        library.scalatest % Test,
        library.scalacheck % Test,
        library.catsScalatest % Test
      )
    )

lazy val scalatest =
  project
    .in(file("./cornichon-scalatest"))
    .dependsOn(core)
    .enablePlugins(SbtScalariform)
    .configs(IntegrationTest)
    .settings(Defaults.itSettings : _*)
    .settings(commonSettings)
    .settings(scalariformSettings)
    .settings(
      name := "cornichon",
      libraryDependencies ++= Seq(
        library.scalatest,
        library.akkHttpCirce % Test
      )
    )

lazy val benchmarks =
  project
    .in(file("./cornichon-benchmarks"))
    .settings(commonSettings)
    .dependsOn(core)
    .settings(noPublishSettings)
    .enablePlugins(JmhPlugin)

lazy val docs =
  project
    .in(file("./cornichon-docs"))
    .settings(
      name := "cornichon-docs",
      unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(benchmarks)
    )
    .dependsOn(core, scalatest)
    .enablePlugins(MicrositesPlugin)
    .enablePlugins(ScalaUnidocPlugin)
    .enablePlugins(GhpagesPlugin)
    .settings(commonSettings)
    .settings(docSettings)
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
      val scalaTest     = "3.0.3"
      val akkaActor     = "2.4.18"
      val akkaHttp      = "10.0.5"
      val cats          = "0.9.0"
      val parboiled     = "2.1.4"
      val akkaSse       = "2.0.0"
      val scalaCheck    = "1.13.5"
      val sangriaCirce  = "1.0.1"
      val circe         = "0.7.1"
      val diffson       = "2.1.2"
      val sangria       = "1.2.0"
      val fansi         = "0.2.3"
      val akkaHttpCirce = "1.15.0"
      val catsScalaTest = "2.2.0"
      val ficus         = "1.4.0"
      val monix         = "2.2.4"
    }
    val akkaActor     = "com.typesafe.akka"   %% "akka-actor"      % Version.akkaActor
    val akkaStream    = "com.typesafe.akka"   %% "akka-stream"     % Version.akkaActor
    val akkaHttpCore  = "com.typesafe.akka"   %% "akka-http-core"  % Version.akkaHttp
    val akkaHttp      = "com.typesafe.akka"   %% "akka-http"       % Version.akkaHttp
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
    val monixExec     = "io.monix"            %% "monix-execution" % Version.monix
  }