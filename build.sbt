import scalariform.formatter.preferences._
import sbt.{Developer, file}
import sbt.Keys.{crossScalaVersions, developers, organizationHomepage, publishMavenStyle, scmInfo, startYear}

//https://tpolecat.github.io/2017/04/25/scalac-flags.html
lazy val compilerOptions = Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-language:implicitConversions",
  "-Ypartial-unification",             // SI-2712
  // https://github.com/scala/bug/issues/10448
  // "-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
  "-Ywarn-numeric-widen" // Warn when numerics are widened.
)

lazy val standardSettings = Seq(
  organization := "com.github.agourlay",
  description := "An extensible Scala DSL for testing JSON HTTP APIs.",
  homepage := Some(url("https://github.com/agourlay/cornichon")),
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.12", "2.12.4"),
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
  useGpg := true,
  pgpSecretRing := file("/Users/agourlay/.pgpKeys/agourlay-privkey.gpg"),
  pgpPublicRing := file("/Users/agourlay/.pgpKeys/agourlay-pubkey.gpg"),
  pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray),
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
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val cornichon =
  project
    .in(file("."))
    .aggregate(core, scalatest, docs, benchmarks, experimental, httpMock, kafka)
    .settings(commonSettings)
    .settings(noPublishSettings)
    .settings(
      unmanagedSourceDirectories.in(Compile) := Nil,
      unmanagedSourceDirectories.in(Test) := Nil
    )

lazy val core =
  project
    .in(file("./cornichon-core"))
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(publishingSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-core",
      libraryDependencies ++= Seq(
        library.http4sClient,
        library.http4sCirce,
        library.catsCore,
        library.catsMacro,
        library.akkaStream,
        library.akkaHttp,
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
        library.monixReactive,
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
    .settings(formattingSettings)
    .settings(
      name := "cornichon",
      libraryDependencies ++= Seq(
        library.scalatest,
        library.http4sServer % Test,
        library.http4sCirce % Test,
        library.http4sDsl % Test
      )
    )

lazy val experimental =
  project
    .in(file("./cornichon-experimental"))
    .dependsOn(core)
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-experimental",
      testFrameworks += new TestFramework("com.github.agourlay.cornichon.experimental.sbtinterface.CornichonFramework"),
      libraryDependencies ++= Seq(
        library.sbtTest
      )
    )

lazy val kafka =
  project
    .in(file("./cornichon-kafka"))
    .dependsOn(core, scalatest % Test)
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-kafka",
      libraryDependencies ++= Seq(
        library.kafkaClient,
        library.kafkaBroker % Test
      )
    )

lazy val httpMock =
  project
    .in(file("./cornichon-http-mock"))
    .dependsOn(core, scalatest % Test)
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-http-mock",
      libraryDependencies ++= Seq(
        library.http4sServer,
        library.http4sCirce,
        library.http4sDsl
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
    .dependsOn(core, scalatest, kafka)
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

lazy val formattingSettings = Seq(
  scalariformAutoformat := true,
  scalariformPreferences := scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
)

lazy val library =
  new {
    object Version {
      val scalaTest     = "3.0.4"
      val akkaStream    = "2.5.9"
      val akkaHttp      = "10.0.11"
      val cats          = "1.0.1"
      val parboiled     = "2.1.4"
      val scalaCheck    = "1.13.5"
      val sangriaCirce  = "1.2.0"
      val circe         = "0.9.1"
      val diffson       = "2.2.5"
      val sangria       = "1.3.3"
      val fansi         = "0.2.5"
      val catsScalaTest = "2.3.1"
      val ficus         = "1.4.3"
      val monix         = "3.0.0-M3"
      val sbtTest       = "1.0"
      val http4s        = "0.18.0-M8"
      val kafkaVersion  = "1.0.0"
    }
    val akkaStream    = "com.typesafe.akka"   %% "akka-stream"              % Version.akkaStream
    val akkaHttp      = "com.typesafe.akka"   %% "akka-http"                % Version.akkaHttp
    val catsMacro     = "org.typelevel"       %% "cats-macros"              % Version.cats
    val catsCore      = "org.typelevel"       %% "cats-core"                % Version.cats
    val scalatest     = "org.scalatest"       %% "scalatest"                % Version.scalaTest
    val ficus         = "com.iheart"          %% "ficus"                    % Version.ficus
    val parboiled     = "org.parboiled"       %% "parboiled"                % Version.parboiled
    val fansi         = "com.lihaoyi"         %% "fansi"                    % Version.fansi
    val sangria       = "org.sangria-graphql" %% "sangria"                  % Version.sangria
    val sangriaCirce  = "org.sangria-graphql" %% "sangria-circe"            % Version.sangriaCirce
    val circeCore     = "io.circe"            %% "circe-core"               % Version.circe
    val circeGeneric  = "io.circe"            %% "circe-generic"            % Version.circe
    val circeParser   = "io.circe"            %% "circe-parser"             % Version.circe
    val diffsonCirce  = "org.gnieh"           %% "diffson-circe"            % Version.diffson
    val scalacheck    = "org.scalacheck"      %% "scalacheck"               % Version.scalaCheck
    val catsScalatest = "com.ironcorelabs"    %% "cats-scalatest"           % Version.catsScalaTest
    val monixExec     = "io.monix"            %% "monix-execution"          % Version.monix
    val monixReactive = "io.monix"            %% "monix-reactive"           % Version.monix
    val sbtTest       = "org.scala-sbt"       %  "test-interface"           % Version.sbtTest
    val http4sClient  = "org.http4s"          %% "http4s-blaze-client"      % Version.http4s
    val http4sServer  = "org.http4s"          %% "http4s-blaze-server"      % Version.http4s
    val http4sCirce   = "org.http4s"          %% "http4s-circe"             % Version.http4s
    val http4sDsl     = "org.http4s"          %% "http4s-dsl"               % Version.http4s
    val kafkaClient   = "org.apache.kafka"    %  "kafka-clients"            % Version.kafkaVersion
    val kafkaBroker   = "net.manub"           %% "scalatest-embedded-kafka" % Version.kafkaVersion
  }
