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
libraryDependencies += "com.github.agourlay" %% "cornichon-test-framework" % "0.16.2" % Test
testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework")
```

```scala
// Mill
object test extends Tests{
  def ivyDeps = Agg(ivy"com.github.agourlay::cornichon-test-framework:0.16.2")
  def testFrameworks = Seq("com.github.agourlay.cornichon.framework.CornichonFramework")
}
```

If you need to integrate with other build tools or want to run the `feature` from your IDE, you can use the the [ScalaTest](http://www.scalatest.org/) flavor.

``` scala
// SBT
libraryDependencies += "com.github.agourlay" %% "cornichon-scalatest" % "0.16.2" % Test
```