{%
laika.title = "CI & Deployment"
%}

# CI & Deployment

## SBT integration

It is recommended to use the SBT command line to run tests:

- `~test` tilde to re-run a command on change.
- `testOnly *MyFeatureSpec` to run only the feature class `MyFeatureSpec`.
- `testOnly *MyFeatureSpec -- "my scenario"` to run only the scenario named `my scenario` from that feature.
- `testOnly *MyFeatureSpec -- "my scenario" "--seed=42"` to replay a scenario with the seed of a previous run.

Scenario names must match exactly, without the feature name. A name that matches nothing runs no scenarios and still reports success.

See [SBT doc](https://www.scala-sbt.org/1.x/docs/Testing.html) for more information.

The `steps` execution logs will only be shown if:
- the scenario fails
- the scenario succeeded and contains at least one `DebugStep` such as `And I show_last_status`

## Running tests without a build tool

When integrating cornichon features in a build pipeline, it can be useful to package those features into a runnable form to avoid the cost of recompilation.

The library ships a main runner at `com.github.agourlay.cornichon.framework.MainRunner`. It discovers the feature classes of a package on the classpath, runs them and writes a JUnit XML report per feature.

Features usually live in `src/test`, so package the test classes and export their classpath:

```bash
sbt "Test/package" "export Test/fullClasspath"
```

`Test/package` produces `target/scala-<version>/<name>-<version>-tests.jar`. In the exported classpath, replace the `test-classes` directory with that jar, then call the runner:

```bash
java -cp "$CLASSPATH" com.github.agourlay.cornichon.framework.MainRunner \
  --packageToScan=com.example.features \
  --reportsOutputDir=target/test-reports
```

`--help` shows:

```
Usage: cornichon-test-framework --packageToScan <string> [--reportsOutputDir <string>] [--featureParallelism <integer>] [--seed <integer>] [--scenarioNameFilter <string>]

Run your cornichon features without SBT.

Options and flags:
    --help
        Display this help text.
    --packageToScan <string>
        Package containing the feature files.
    --reportsOutputDir <string>
        Output directory for junit.xml files (defaults to current).
    --featureParallelism <integer>
        Number of features running in parallel (default=1).
    --seed <integer>
        Seed to use for starting random processes.
    --scenarioNameFilter <string>
        Filter scenario to run by name.
```

Notes:

- `--packageToScan` also scans its sub-packages. Use `--packageToScan ""` for features declared without a package.
- `--scenarioNameFilter` takes the exact scenario name, like `testOnly` does.
- The replay command printed after a run uses the sbt syntax. Without sbt, pass the same values to `--scenarioNameFilter` and `--seed`.
- The process exits with `0` when all scenarios succeed and `1` otherwise. It also exits with `1` when the arguments are invalid, when no feature is found in the package, or when `--scenarioNameFilter` matches no scenario.

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

After creating `docker.sbt`, run `sbt Docker/publishLocal` to build the Docker image locally.
