{%
laika.title = "CI & Deployment"
%}

# CI & Deployment

## SBT integration

It is recommended to use the CLI from SBT to trigger tests:

- `~test` tilde to re-run a command on change.
- `testOnly *CornichonExamplesSpec` to run only the feature CornichonExamplesSpec.
- `testOnly *CornichonExamplesSpec -- "Cornichon feature example should CRUD Feature demo"` to run only the scenario `CRUD Feature demo` from the feature `Cornichon feature example`.

The full name of a scenario is `feature-name should scenario-name`.

See [SBT doc](https://www.scala-sbt.org/1.x/docs/Testing.html) for more information.

The `steps` execution logs will only be shown if:
- the scenario fails
- the scenario succeeded and contains at least one `DebugStep` such as `And I show_last_status`

## Running tests without a build tool

When integrating cornichon features in a build pipeline, it can be useful to package those features into a runnable form to avoid the cost of recompilation.

The library ships a main runner at `com.github.agourlay.cornichon.framework.MainRunner`.

Once your project is packaged as a jar file, calling the main runner with `--help` shows:

```
Usage: cornichon-test-framework --packageToScan <string> [--featureParallelism <integer>] [--seed <integer>] [--scenarioNameFilter <string>]

Run your cornichon features without SBT.

Options and flags:
    --help
        Display this help text.
    --packageToScan <string>
        Package containing the feature files.
    --reportsOutputDir <string>
       Output directory for junit.xml files (defaults to current).
    --featureParallelism <integer>
        Number of feature running in parallel (default=1).
    --seed <integer>
        Seed to use for starting random processes.
    --scenarioNameFilter <string>
        Filter scenario to run by name.
```

## Packaging tests in a Docker container

You can package your test suite as a Docker image using `sbt-native-packager`. Place these settings in a `docker.sbt` in the root of your project.

```scala
import NativePackagerHelper._

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework"),
    Compile / mainClass := Some("com.github.agourlay.cornichon.framework.MainRunner"),

    scriptClasspath ++= {
      fromClasspath((Test / managedClasspath).value, ".", _ => true).map(_._2) :+
        (Test / sbt.Keys.`package`).value.getName
    },

    Universal / mappings ++= {
      val testJar = (Test / sbt.Keys.`package`).value
      fromClasspath((Test / managedClasspath).value, "lib", _ => true) :+
        (testJar -> s"lib/${testJar.getName}")
    },
    dockerCmd := Seq(
      "--packageToScan=$your-root-package",
      "--reportsOutputDir=/target/test-reports"
    )
  )
```

After creating `docker.sbt`, run `sbt docker:publishLocal` to build the Docker image locally.
