import scalariform.formatter.preferences._
import sbt.{Developer, file}
import sbt.Keys.{developers, organizationHomepage, publishMavenStyle, scmInfo, startYear}

//https://tpolecat.github.io/2017/04/25/scalac-flags.html
lazy val compilerOptions = Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfuture",                          // Turn on future language features.
  "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:package-object-classes",     // Class or object defined in package object. (got a macro there)
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",              // Pattern match may not be typesafe.
  "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification",             // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused",
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
)

lazy val standardSettings = Seq(
  organization := "com.github.agourlay",
  description := "An extensible Scala DSL for testing JSON HTTP APIs.",
  homepage := Some(url("https://github.com/agourlay/cornichon")),
  scalaVersion := "2.12.10",
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
  )),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  // To profile tests execution
  //javaOptions in Test := Seq("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder")
)

lazy val publishingSettings = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value, //key and passphrase could be exported/encoded to enable someone else to do a release
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
    .aggregate(core, scalatest, docs, benchmarks, testFramework, httpMock, kafka)
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
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "1"),
      testFrameworks += new TestFramework("utest.runner.Framework"),
      libraryDependencies ++= Seq(
        library.http4sClient,
        library.http4sCirce,
        library.catsCore,
        library.catsMacro,
        library.pureConfig,
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
        library.caffeine,
        library.utest % Test,
        library.claimant % Test,
        library.scalacheck % Test,
        library.circeTesting % Test
      )
    )

lazy val scalatest =
  project
    .in(file("./cornichon-scalatest"))
    .dependsOn(core)
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-scalatest",
      libraryDependencies ++= Seq(
        library.scalatest
      )
    )

lazy val testFramework =
  project
    .in(file("./cornichon-test-framework"))
    .dependsOn(core)
    .configs(IntegrationTest)
    .settings(Defaults.itSettings : _*)
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-test-framework",
      testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework"),
      libraryDependencies ++= Seq(
        library.sbtTest,
        library.openPojo,
        library.decline,
        library.scalaXml,
        library.scalacheck % Test,
        library.http4sServer % Test,
        library.http4sCirce % Test,
        library.http4sDsl % Test
      )
    )

lazy val kafka =
  project
    .in(file("./cornichon-kafka"))
    .dependsOn(core, testFramework % Test)
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-kafka",
      testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework"),
      libraryDependencies ++= Seq(
        library.kafkaClient,
        library.kafkaBroker % Test
      )
    )

lazy val httpMock =
  project
    .in(file("./cornichon-http-mock"))
    .dependsOn(core, testFramework % Test)
    .enablePlugins(SbtScalariform)
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      name := "cornichon-http-mock",
      testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework"),
      libraryDependencies ++= Seq(
        library.http4sServer,
        library.http4sCirce,
        library.http4sDsl
      )
    )

lazy val benchmarks =
  project
    .in(file("./benchmarks"))
    .settings(commonSettings)
    .dependsOn(core)
    .settings(noPublishSettings)
    .enablePlugins(JmhPlugin)

lazy val docs =
  project
    .in(file("./cornichon-docs"))
    .settings(
      name := "cornichon-docs",
      unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(benchmarks, scalatest),
      micrositeDocumentationLabelDescription := "Scaladoc",
      micrositeCompilingDocsTool := WithTut
    )
    .dependsOn(core, testFramework, kafka, httpMock)
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
    .setPreference(DanglingCloseParenthesis, Preserve)
)

lazy val library =
  new {
    object Version {
      val claimant      = "0.1.2"
      val scalaTest     = "3.1.0"
      val utest         = "0.7.2"
      val cats          = "2.0.0"
      val parboiled     = "2.1.8"
      val scalaCheck    = "1.14.3"
      val sangriaCirce  = "1.3.0"
      val circe         = "0.12.3"
      val diffson       = "4.0.1"
      val sangria       = "2.0.0-M1"
      val fansi         = "0.2.7"
      val pureConfig    = "0.12.1"
      val monix         = "3.1.0"
      val sbtTest       = "1.0"
      val http4s        = "0.21.0-M6"
      val embeddedKafka = "2.4.0"
      val kafkaClient   = "2.4.0"
      val caffeine      = "2.8.0"
      val openPojo      = "0.8.13"
      val decline       = "1.0.0"
      val scalaXml      = "2.0.0-M1"
    }
    val claimant      = "org.typelevel"                  %% "claimant"             % Version.claimant
    val catsMacro     = "org.typelevel"                  %% "cats-macros"          % Version.cats
    val catsCore      = "org.typelevel"                  %% "cats-core"            % Version.cats
    val scalatest     = "org.scalatest"                  %% "scalatest"            % Version.scalaTest
    val utest         = "com.lihaoyi"                    %% "utest"                % Version.utest
    val pureConfig    = "com.github.pureconfig"          %% "pureconfig"           % Version.pureConfig
    val parboiled     = "org.parboiled"                  %% "parboiled"            % Version.parboiled
    val fansi         = "com.lihaoyi"                    %% "fansi"                % Version.fansi
    val sangria       = "org.sangria-graphql"            %% "sangria"              % Version.sangria
    val sangriaCirce  = "org.sangria-graphql"            %% "sangria-circe"        % Version.sangriaCirce
    val circeCore     = "io.circe"                       %% "circe-core"           % Version.circe
    val circeGeneric  = "io.circe"                       %% "circe-generic"        % Version.circe
    val circeParser   = "io.circe"                       %% "circe-parser"         % Version.circe
    val circeTesting  = "io.circe"                       %% "circe-testing"        % Version.circe
    val diffsonCirce  = "org.gnieh"                      %% "diffson-circe"        % Version.diffson
    val scalacheck    = "org.scalacheck"                 %% "scalacheck"           % Version.scalaCheck
    val monixExec     = "io.monix"                       %% "monix-execution"      % Version.monix
    val monixReactive = "io.monix"                       %% "monix-reactive"       % Version.monix
    val sbtTest       = "org.scala-sbt"                  %  "test-interface"       % Version.sbtTest
    val http4sClient  = "org.http4s"                     %% "http4s-blaze-client"  % Version.http4s
    val http4sServer  = "org.http4s"                     %% "http4s-blaze-server"  % Version.http4s
    val http4sCirce   = "org.http4s"                     %% "http4s-circe"         % Version.http4s
    val http4sDsl     = "org.http4s"                     %% "http4s-dsl"           % Version.http4s
    val kafkaClient   = "org.apache.kafka"               %  "kafka-clients"        % Version.kafkaClient
    val kafkaBroker   = "io.github.embeddedkafka"        %% "embedded-kafka"       % Version.embeddedKafka
    val caffeine      = "com.github.ben-manes.caffeine"  %  "caffeine"             % Version.caffeine
    val openPojo      = "com.openpojo"                   %  "openpojo"             % Version.openPojo
    val decline       = "com.monovore"                   %% "decline"              % Version.decline
    val scalaXml      = "org.scala-lang.modules"         %% "scala-xml"            % Version.scalaXml
  }
