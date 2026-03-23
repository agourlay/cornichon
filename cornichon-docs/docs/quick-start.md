{%
laika.title = Quick start
%}

# Quick start

## Installation

Cornichon is available for Scala 2.13 & Scala 3.

The library is compatible with [SBT](https://www.scala-sbt.org/) and [Mill](http://www.lihaoyi.com/mill/).

``` scala
// SBT
libraryDependencies += "com.github.agourlay" %% "cornichon-test-framework" % "0.23.0" % Test
testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework")
```

```scala
// Mill
object test extends Tests {
  def ivyDeps = Agg(ivy"com.github.agourlay::cornichon-test-framework:0.23.0")
  def testFrameworks = Seq("com.github.agourlay.cornichon.framework.CornichonFramework")
}
```

## Your first test

A Cornichon test is a class extending `CornichonFeature` and implementing the required `feature` function. In SBT, these classes live inside `src/test/scala` and can be run using `sbt test`.

A `feature` can have several `scenarios` which in turn can have several `steps`.

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature

class CornichonExamplesSpec extends CornichonFeature {

  def feature = Feature("Checking google"){

    Scenario("Google is up and running"){

      When I get("http://google.com")

      Then assert status.is(302)
    }
  }
}
```

### Scala 3 braceless syntax

When using Scala 3, you can take advantage of significant indentation to drop the curly braces, resulting in a style closer to Cucumber/Gherkin:

```scala
import com.github.agourlay.cornichon.CornichonFeature

class CornichonExamplesSpec extends CornichonFeature:

  def feature = Feature("Checking google"):

    Scenario("Google is up and running"):

      When I get("http://google.com")

      Then assert status.is(302)
```

Both styles are equivalent; choose whichever your team prefers.

## Failure modes

- A `feature` fails if one or more `scenarios` fail.
- A `scenario` fails if at least one `step` fails.
- A `scenario` will stop at the first failed step encountered and ignore the remaining `steps`.

## Next steps

- [DSL](dsl.md) — full reference of available steps
- [Understanding Test Output](test-output.md) — how to read failure messages
- [Feature Options](feature-options.md) — configure test execution
- [Common Patterns](common-patterns.md) — recipes for real-world testing scenarios
