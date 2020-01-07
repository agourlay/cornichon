---
layout: docs
title:  "Misc."
position: 10
---

# Custom HTTP body type

By default the HTTP DSL expects a String body but in some cases you might want to work at a higher level of abstraction.

In order to use a custom type as body, it is necessary to provide 3 typeclass instances:

- `cats.Show` used to print the values
- `io.circe.Encoder` used to convert the values to JSON
- `com.github.agourlay.cornichon.resolver.Resolvable` used to provide a custom String representation in which placeholders can be resolved

For instance if you wish to use the `JsObject` from `play-json` as HTTP request's body you can define the following instances in your code:

```scala
  lazy implicit val jsonResolvableForm = new Resolvable[JsObject] {
    def toResolvableForm(s: JsObject) = s.toString()
    def fromResolvableForm(s: String) = Json.parse(s).as[JsObject]
  }

  lazy implicit val showJson = new Show[JsObject] {
    override def show(f: JsObject): String = f.toString()
  }

  lazy implicit val JsonEncoder:Encoder[JsObject] = new Encoder[JsObject] {
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

# Packaging features (only for cornichon-scalatest)

When integrating cornichon features in a build pipeline, it can be interesting to package those features in a runnable forms to avoid the cost of recompilation.

You can find below an example of Docker packaging done using `sbt-native-packager` and a built-in Teamcity reporter. You can place these settings in a `docker.sbt` in the root of your project.

This should hopefully inspire you to setup your own solution or contribute to improve this one.

```scala
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker => NPDocker}
import NativePackagerHelper._

enablePlugins(JavaAppPackaging, DockerPlugin)

dockerBaseImage := "develar/java"
dockerUpdateLatest := true
version in NPDocker := ("git rev-parse HEAD" !!).trim
mainClass in Compile := Some("org.scalatest.tools.Runner")

dockerCmd := Seq(
  "-C",
  "com.github.agourlay.cornichon.scalatest.TeamcityReporter",
  "-R",
  s"lib/${(artifactPath in (Test, packageBin)).value.getName}")

// Install `bash` to be able to start the application

dockerCommands := Seq(
  dockerCommands.value.head,
  Cmd("RUN apk add --update bash && rm -rf /var/cache/apk/*")
) ++ dockerCommands.value.tail

// Include `Test` classpath

scriptClasspath ++=
  fromClasspath((managedClasspath in Test).value, ".", _ ⇒ true).map(_._2) :+
    (sbt.Keys.`package` in Test).value.getName

mappings in Universal ++= {
  val testJar = (sbt.Keys.`package` in Test).value

  fromClasspath((managedClasspath in Test).value, "lib", _ ⇒ true) :+
    (testJar → s"lib/${testJar.getName}")
}
```

After you created the `docker.sbt`, just run `sbt docker:publishLocal` in order to create a docker image locally.