---
layout: docs
title:  "Installation"
position: 1
---

# Installation

Cornichon is cross-built for Scala 2.11, and 2.12.

- For the [ScalaTest](http://www.scalatest.org/) flavor which integrates nicely with various build tools and CI pipeline:

``` scala
// SBT
libraryDependencies += "com.github.agourlay" %% "cornichon-scalatest" % "0.16.1" % Test
```

And Maven through the [ScalaTest Maven plugin](http://www.scalatest.org/user_guide/using_the_scalatest_maven_plugin).

- For a more lightweight version without ScalaTest which works only with SBT and [Mill](http://www.lihaoyi.com/mill/):

``` scala
// SBT
libraryDependencies += "com.github.agourlay" %% "cornichon-test-framework" % "0.16.1" % Test
testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework")
```

```scala
// Mill
object test extends Tests{
  def ivyDeps = Agg(ivy"com.github.agourlay::cornichon-test-framework:0.16.1")
  def testFrameworks = Seq("com.github.agourlay.cornichon.framework.CornichonFramework")
}
```
