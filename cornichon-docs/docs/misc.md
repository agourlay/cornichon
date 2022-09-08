---
layout: docs
title:  "Misc."
position: 10
---

# Custom HTTP body type

By default, the HTTP DSL expects a String body but in some cases you might want to work at a higher level of abstraction.

In order to use a custom type as body, it is necessary to provide 3 typeclass instances:

- `cats.Show` used to print the values
- `io.circe.Encoder` used to convert the values to JSON
- `com.github.agourlay.cornichon.resolver.Resolvable` used to provide a custom String representation in which placeholders can be resolved

For instance if you wish to use the `JsObject` from `play-json` as HTTP request's body you can define the following instances in your code:

```scala
  lazy implicit val jsonResolvableForm: Resolvable[JsObject] = new Resolvable[JsObject] {
    override def toResolvableForm(s: JsObject) = s.toString()
    override def fromResolvableForm(s: String) = Json.parse(s).as[JsObject]
  }

  lazy implicit val showJson: Show[JsObject] = new Show[JsObject] {
    override def show(f: JsObject): String = f.toString()
  }

  lazy implicit val JsonEncoder: Encoder[JsObject] = new Encoder[JsObject] {
    override def apply(a: JsObject): Json = parse(a.toString()).getOrElse(cJson.Null)
  }
```


# SBT integration

It is recommended to use the nice CLI from SBT to trigger tests:

- `~test` tilde to re-run a command on change.
- `testOnly *CornichonExamplesSpec` to run only the feature CornichonExamplesSpec.
- `testOnly *CornichonExamplesSpec -- "Cornichon feature example should CRUD Feature demo"` to run only the scenario `CRUD Feature demo` from the feature `Cornichon feature example`.

The full name of a scenario is `feature-name should scenario-name`.

See [SBT doc](http://www.scala-sbt.org/0.13/docs/Testing.html) for more information.

The `steps` execution logs will only be shown if:
- the scenario fails
- the scenario succeeded and contains at least one `DebugStep` such as `And I show_last_status`

# Running tests without a build tool

When integrating cornichon features in a build pipeline, it can be interesting to package those features into a runnable forms to avoid the cost of recompilation.

The library ships a main runner that can be used to run the tests without build tool.

It can be found as `com.github.agourlay.cornichon.framework.MainRunner`.

Once your project is packaged as a jar file, calling the main runner with `--help` shows the following info:

```
Usage: cornichon-test-framework --packageToScan <string> [--featureParallelism <integer>] [--seed <integer>] [--scenarioNameFilter <string>]

Run your cornichon features without SBT.

Options and flags:
    --help
        Display this help text.
    --packageToScan <string>
        Package containing the feature files.
    --reportsOutputDir <string>
       Output directory for junit.xml files (default to current).
    --featureParallelism <integer>
        Number of feature running in parallel (default=1).
    --seed <integer>
        Seed to use for starting random processes.
    --scenarioNameFilter <string>
        Filter scenario to run by name.
```

# Packaging features in a Docker container

You can find below an example of Docker packaging done using `sbt-native-packager`.
 
You can place these settings in a `docker.sbt` in the root of your project.

This should hopefully inspire you to set up your own solution or contribute to improve this one.

```scala
import NativePackagerHelper._

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework"),
    mainClass in Compile := Some("com.github.agourlay.cornichon.framework.MainRunner"),

    scriptClasspath ++= {
      fromClasspath((managedClasspath in Test).value, ".", _ => true).map(_._2) :+
        (sbt.Keys.`package` in Test).value.getName
    },

    mappings in Universal ++= {
      val testJar = (sbt.Keys.`package` in Test).value
      fromClasspath((managedClasspath in Test).value, "lib", _ => true) :+
        (testJar -> s"lib/${testJar.getName}")
    },

    noPackageDoc,
    dockerCmd := Seq(
      "--packageToScan=$your-root-package",
      "--reportsOutputDir=/target/test-reports"
    )
  )
```

After you created the `docker.sbt`, just run `sbt docker:publishLocal` in order to create a docker image locally.
