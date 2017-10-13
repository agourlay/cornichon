import scalariform.formatter.preferences._
import sbt.{Developer, file}
import sbt.Keys.{crossScalaVersions, developers, organizationHomepage, publishMavenStyle, scmInfo, startYear}

//https://tpolecat.github.io/2017/04/25/scalac-flags.html
lazy val compilerOptions = Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  //"-feature",                          // Emit warning and location for usages of features that should be imported explicitly. (nice for DSLs)
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  //"-Xfatal-warnings",                  // Fail the compilation if there are any warnings. (too hardcore due to the 2.11 cross compilation Either madness)
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
  //"-Xlint:package-object-classes",     // Class or object defined in package object. (got a macro there)
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",              // Pattern match may not be typesafe.
  "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification",             // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  //"-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined. (not for scala 2.11)
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused",
  "-Ywarn-unused-import"
  //"-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused. (not for scala 2.11)
  //"-Ywarn-unused:imports",             // Warn if an import selector is not referenced. (not for scala 2.11)
  //"-Ywarn-unused:locals",              // Warn if a local definition is unused. (not for scala 2.11)
  //"-Ywarn-unused:params",              // Warn if a value parameter is unused. (not for scala 2.11)
  //"-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused. (not for scala 2.11)
  //"-Ywarn-unused:privates",            // Warn if a private member is unused. (not for scala 2.11)
  //"-Ywarn-value-discard"               // Warn when non-Unit expression results are unused. (not for scala 2.11)
)

lazy val standardSettings = Seq(
  organization := "com.github.agourlay",
  description := "An extensible Scala DSL for testing JSON HTTP APIs.",
  homepage := Some(url("https://github.com/agourlay/cornichon")),
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
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
    .aggregate(core, scalatest, docs, benchmarks, experimental, httpMock)
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
    .settings(formattingSettings)
    .settings(
      name := "cornichon-core",
      libraryDependencies ++= Seq(
        library.http4sClient,
        library.http4sCirce,
        library.catsCore,
        library.catsMacro,
        library.akkaActor,
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
        library.monixCats,
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
        library.akkaHttp % Test,
        library.akkaHttpCirce % Test
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

lazy val formattingSettings = scalariformSettings(autoformat = true) ++ Seq(
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
      val akkaActor     = "2.5.6"
      val akkaHttp      = "10.0.10"
      val cats          = "0.9.0"
      val parboiled     = "2.1.4"
      val scalaCheck    = "1.13.5"
      val sangriaCirce  = "1.1.0"
      val circe         = "0.8.0"
      val diffson       = "2.2.2"
      val sangria       = "1.3.0"
      val fansi         = "0.2.5"
      val akkaHttpCirce = "1.17.0"
      val catsScalaTest = "2.2.0"
      val ficus         = "1.4.2"
      val monix         = "2.3.0"
      val sbtTest       = "1.0"
      val http4s        = "0.17.5"
    }
    val akkaActor     = "com.typesafe.akka"     %% "akka-actor"           % Version.akkaActor
    val akkaStream    = "com.typesafe.akka"     %% "akka-stream"          % Version.akkaActor
    val akkaHttp      = "com.typesafe.akka"     %% "akka-http"            % Version.akkaHttp
    val akkaHttpCirce = "de.heikoseeberger"     %% "akka-http-circe"      % Version.akkaHttpCirce
    val catsMacro     = "org.typelevel"         %% "cats-macros"          % Version.cats
    val catsCore      = "org.typelevel"         %% "cats-core"            % Version.cats
    val scalatest     = "org.scalatest"         %% "scalatest"            % Version.scalaTest
    val ficus         = "com.iheart"            %% "ficus"                % Version.ficus
    val parboiled     = "org.parboiled"         %% "parboiled"            % Version.parboiled
    val fansi         = "com.lihaoyi"           %% "fansi"                % Version.fansi
    val sangria       = "org.sangria-graphql"   %% "sangria"              % Version.sangria
    val sangriaCirce  = "org.sangria-graphql"   %% "sangria-circe"        % Version.sangriaCirce
    val circeCore     = "io.circe"              %% "circe-core"           % Version.circe
    val circeGeneric  = "io.circe"              %% "circe-generic"        % Version.circe
    val circeParser   = "io.circe"              %% "circe-parser"         % Version.circe
    val diffsonCirce  = "org.gnieh"             %% "diffson-circe"        % Version.diffson
    val scalacheck    = "org.scalacheck"        %% "scalacheck"           % Version.scalaCheck
    val catsScalatest = "com.ironcorelabs"      %% "cats-scalatest"       % Version.catsScalaTest
    val monixExec     = "io.monix"              %% "monix-execution"      % Version.monix
    val monixReactive = "io.monix"              %% "monix-reactive"       % Version.monix
    val monixCats     = "io.monix"              %% "monix-cats"           % Version.monix
    val sbtTest       = "org.scala-sbt"         %  "test-interface"       % Version.sbtTest
    val http4sClient  = "org.http4s"            %% "http4s-blaze-client"  % Version.http4s
    val http4sServer  = "org.http4s"            %% "http4s-blaze-server"  % Version.http4s
    val http4sCirce   = "org.http4s"            %% "http4s-circe"         % Version.http4s
    val http4sDsl     = "org.http4s"            %% "http4s-dsl"           % Version.http4s
  }
