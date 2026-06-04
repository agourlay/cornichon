import sbt.{file, Developer}
import sbt.Keys.{developers, organizationHomepage, publishMavenStyle, scmInfo, startYear}
import laika.helium.Helium
import laika.helium.config.{ButtonLink, Favicon, HeliumIcon, IconLink, ImageLink, LinkPanel, ReleaseInfo, Teaser, TextLink}
import laika.ast.{Image, InternalTarget, LengthUnit, Path}
import laika.ast.Path.Root
import laika.theme.config.Color

val compilerOptions = Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:implicitConversions",
  "-java-output-version:11", // pin bytecode target so the JDK in use can't silently change it
  "-Wunused:all",
  "-Wnonunit-statement",
  "-Wvalue-discard",
  // sangria's deriveObjectType macro consumes circe-generic auto-derived implicits the compiler can't see
  "-Wconf:msg=unused import&src=.*GraphQLSuperMicroService\\.scala:s"
)

lazy val standardSettings = Seq(
  organization := "com.github.agourlay",
  description := "An extensible Scala DSL for testing JSON HTTP APIs.",
  homepage := Some(url("https://github.com/agourlay/cornichon")),
  scalaVersion := "3.3.7",
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  Test / fork := true,
  scalacOptions ++= compilerOptions,
  Test / scalacOptions -= "-Wnonunit-statement", // too noisy with Scalacheck
  // Additional meta-info required by maven central
  startYear := Some(2015),
  organizationHomepage := Some(url("https://github.com/agourlay/cornichon")),
  developers := Developer("agourlay", "Arnaud Gourlay", "", url("https://github.com/agourlay")) :: Nil,
  scmInfo := Some(
    ScmInfo(
      browseUrl = url("https://github.com/agourlay/cornichon.git"),
      connection = "scm:git:git@github.com:agourlay/cornichon.git"
    )
  )
)

lazy val publishingSettings = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value, // key and passphrase could be exported/encoded to enable someone else to do a release
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := (_ => false),
  publishTo := {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
    else localStaging.value
  }
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publish / skip := true
)

// disable cats-effect IO stack traces to avoid polluting the bench/profiling results
lazy val noCatsEffectTracing = Seq(
  javaOptions += "-Dcats.effect.tracing.mode=none"
)

lazy val cornichon =
  project
    .in(file("."))
    .aggregate(core, docs, benchmarks, testFramework, httpMock)
    .settings(standardSettings)
    .settings(noPublishSettings)
    .settings(
      Compile / unmanagedSourceDirectories := Nil,
      Test / unmanagedSourceDirectories := Nil
    )

lazy val core =
  project
    .in(file("./cornichon-core"))
    .settings(standardSettings)
    .settings(publishingSettings)
    .settings(
      name := "cornichon-core",
      Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "1", "-minSuccessfulTests", "500"),
      libraryDependencies ++= Seq(
        library.http4sClient,
        library.http4sCirce,
        library.fs2Io,
        library.catsCore,
        library.catsEffect,
        library.parboiled,
        library.fansi,
        library.sangria,
        library.sangriaCirce,
        library.circeCore,
        library.circeGeneric,
        library.circeParser,
        library.diffsonCirce,
        library.caffeine,
        library.typesafeConfig,
        library.munit % Test,
        library.scalacheck % Test,
        library.circeTesting % Test
      )
    )

lazy val testFramework =
  project
    .in(file("./cornichon-test-framework"))
    .dependsOn(core)
    .settings(noCatsEffectTracing)
    .settings(standardSettings)
    .settings(publishingSettings)
    .settings(
      name := "cornichon-test-framework",
      testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework"),
      libraryDependencies ++= Seq(
        library.sbtTest,
        library.fs2Core,
        library.catsEffect,
        library.openPojo,
        library.decline,
        library.scalaXml,
        library.scalacheck % Test,
        library.http4sServer % Test,
        library.http4sCirce % Test,
        library.http4sDsl % Test
      )
    )

lazy val httpMock =
  project
    .in(file("./cornichon-http-mock"))
    .dependsOn(core, testFramework % Test)
    .settings(standardSettings)
    .settings(publishingSettings)
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
    .settings(standardSettings)
    .settings(noCatsEffectTracing)
    .dependsOn(core)
    .settings(noPublishSettings)
    .enablePlugins(JmhPlugin)

lazy val docs =
  project
    .in(file("./cornichon-docs"))
    .dependsOn(core, testFramework, httpMock)
    .enablePlugins(LaikaPlugin, MdocPlugin, GhpagesPlugin)
    .settings(standardSettings)
    .settings(noPublishSettings)
    .settings(
      mdocIn := baseDirectory.value / "docs",
      Laika / sourceDirectories := Seq(mdocOut.value),
      laikaTheme := Helium.defaults.all
        .metadata(
          title = Some("Cornichon"),
          description = Some("An extensible Scala DSL for testing JSON HTTP APIs."),
          language = Some("en")
        )
        // Light mode colors only — dark mode uses Helium defaults.
        // .all.themeColors sets light mode for all formats (HTML, PDF, EPUB).
        // Dark mode is configured separately via .all.darkMode.themeColors.
        .all
        .themeColors(
          primary = Color.hex("4A8C6E"),
          secondary = Color.hex("2D6B4F"),
          primaryMedium = Color.hex("93C5AA"),
          primaryLight = Color.hex("F2F6F4"),
          text = Color.hex("3D4450"),
          background = Color.hex("E4E6E7"),
          bgGradient = (Color.hex("3A7D5C"), Color.hex("1E3A2F"))
        )
        .site
        .landingPage(
          logo = Some(Image(InternalTarget(Root / "img" / "cornichon-logo.png"), width = Some(LengthUnit.px(150)), height = Some(LengthUnit.px(150)))),
          title = Some("Cornichon"),
          subtitle = Some("An extensible Scala DSL for testing JSON HTTP APIs."),
          latestReleases = Seq(ReleaseInfo("Latest Release", "0.24.0")),
          license = Some("Apache-2.0"),
          titleLinks = Seq(
            ButtonLink.internal(Root / "quick-start.md", "Get Started"),
            IconLink.external("https://github.com/agourlay/cornichon", HeliumIcon.github)
          ),
          linkPanel = Some(
            LinkPanel(
              title = "Documentation",
              links = Seq(
                TextLink.internal(Root / "quick-start.md", "Quick Start"),
                TextLink.internal(Root / "dsl" / "README.md", "DSL"),
                TextLink.internal(Root / "custom-steps" / "README.md", "Custom Steps"),
                TextLink.internal(Root / "syntax" / "README.md", "Syntax Reference")
              )
            )
          ),
          projectLinks = Nil,
          teasers = Seq(
            Teaser("Expressive DSL", "Write readable integration tests using a Scala DSL inspired by Gherkin."),
            Teaser("JSON First", "Powerful JSON assertions with path expressions, matchers, and ignoring keys."),
            Teaser("Detailed Error Reports", "Failures show JSON diffs, step execution traces, and replay commands."),
            Teaser("Property Based Testing", "Generate and explore random test scenarios with built-in PBT support.")
          )
        )
        .site
        .internalCSS(Root / "css" / "landing.css")
        .site
        .favIcons(
          Favicon.internal(Root / "img" / "favicon.png", "32x32")
        )
        .site
        .mainNavigation(depth = 3)
        .site
        .topNavigationBar(
          homeLink = ImageLink
            .external("index.html", Image(InternalTarget(Root / "img" / "cornichon-logo.png"), width = Some(LengthUnit.px(50)), height = Some(LengthUnit.px(50)))),
          navLinks = Seq(
            IconLink.external("https://javadoc.io/doc/com.github.agourlay/cornichon-core_3/latest/index.html", HeliumIcon.api),
            IconLink.external("https://github.com/agourlay/cornichon", HeliumIcon.github)
          )
        )
        .build,
      laikaExtensions := Seq(
        laika.format.Markdown.GitHubFlavor,
        laika.config.SyntaxHighlighting
      ),
      laikaSite := {
        val result = laikaSite.value
        // GitHub Pages uses Jekyll by default which ignores some files/dirs.
        // .nojekyll tells GitHub Pages to serve all files as-is.
        IO.touch(target.value / "docs" / "site" / ".nojekyll")
        result
      },
      git.remoteRepo := "git@github.com:agourlay/cornichon.git",
      // Override makeSite mappings to include all Laika output (fonts, CSS, etc.)
      makeSite / mappings := {
        val siteDir = target.value / "docs" / "site"
        val files = (siteDir ** AllPassFilter).get.filterNot(_.isDirectory)
        files.map(f => (f, siteDir.toPath.relativize(f.toPath).toString))
      }
    )

lazy val library =
  new {
    object Version {
      val munit = "1.3.1"
      val cats = "2.13.0"
      val catsEffect = "3.7.0"
      val parboiled = "2.5.1"
      val scalaCheck = "1.19.0"
      val sangriaCirce = "1.3.2"
      val circe = "0.14.15"
      val diffson = "4.7.0"
      val sangria = "4.2.18"
      val fansi = "0.5.1"
      val sbtTest = "1.0"
      val http4s = "0.23.34"
      val fs2 = "3.13.0"
      val openPojo = "0.9.1"
      val decline = "2.6.2"
      val scalaXml = "2.4.0"
      val typesafeConfig = "1.4.9"
      val caffeine = "3.2.4"
    }
    val catsCore = "org.typelevel" %% "cats-core" % Version.cats
    val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect
    val munit = "org.scalameta" %% "munit" % Version.munit
    val parboiled = "org.parboiled" %% "parboiled" % Version.parboiled
    val fansi = "com.lihaoyi" %% "fansi" % Version.fansi
    val sangria = "org.sangria-graphql" %% "sangria" % Version.sangria
    val sangriaCirce = "org.sangria-graphql" %% "sangria-circe" % Version.sangriaCirce
    val circeCore = "io.circe" %% "circe-core" % Version.circe
    val circeGeneric = "io.circe" %% "circe-generic" % Version.circe
    val circeParser = "io.circe" %% "circe-parser" % Version.circe
    val circeTesting = "io.circe" %% "circe-testing" % Version.circe
    val diffsonCirce = "org.gnieh" %% "diffson-circe" % Version.diffson
    val scalacheck = "org.scalacheck" %% "scalacheck" % Version.scalaCheck
    val sbtTest = "org.scala-sbt" % "test-interface" % Version.sbtTest
    val http4sClient = "org.http4s" %% "http4s-ember-client" % Version.http4s
    val http4sServer = "org.http4s" %% "http4s-ember-server" % Version.http4s
    val http4sCirce = "org.http4s" %% "http4s-circe" % Version.http4s
    val http4sDsl = "org.http4s" %% "http4s-dsl" % Version.http4s
    val fs2Io = "co.fs2" %% "fs2-io" % Version.fs2
    val fs2Core = "co.fs2" %% "fs2-core" % Version.fs2
    val openPojo = "com.openpojo" % "openpojo" % Version.openPojo
    val decline = "com.monovore" %% "decline" % Version.decline
    val scalaXml = "org.scala-lang.modules" %% "scala-xml" % Version.scalaXml
    val typesafeConfig = "com.typesafe" % "config" % Version.typesafeConfig
    val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % Version.caffeine
  }
