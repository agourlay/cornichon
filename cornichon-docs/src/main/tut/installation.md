---
layout: docs
title:  "Installation"
position: 1
---

# Installation

Cornichon is only available for Scala 2.12.

It is recommended to use Cornichon with `cornichon-test-framework` via `SBT` or `Mill`.

``` scala
// SBT
libraryDependencies += "com.github.agourlay" %% "cornichon-test-framework" % "0.16.3" % Test
testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework")
```

```scala
// Mill
object test extends Tests{
  def ivyDeps = Agg(ivy"com.github.agourlay::cornichon-test-framework:0.16.3")
  def testFrameworks = Seq("com.github.agourlay.cornichon.framework.CornichonFramework")
}
```

The alternative way is to use the [ScalaTest](http://www.scalatest.org/) flavor if you need to:
- use other build tools (Maven with [ScalaTest Maven plugin](http://www.scalatest.org/user_guide/using_the_scalatest_maven_plugin))
- run the `Feature` from your IDE
- package features in an executable jars
- have `HTML` reports

``` scala
// SBT
libraryDependencies += "com.github.agourlay" %% "cornichon-scalatest" % "0.16.3" % Test
```

note: `cornichon-test-framework` will eventually support those additional use cases and `cornichon-scalatest` will be discontinued.