---
layout: docs
title:  "Installation"
position: 1
---

# Installation

Cornichon is available for Scala 2.12 & Scala 2.13.

The library is compatible with [SBT](https://www.scala-sbt.org/) and [Mill](http://www.lihaoyi.com/mill/).

``` scala
// SBT
libraryDependencies += "com.github.agourlay" %% "cornichon-test-framework" % "0.19.5" % Test
testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework")
```

```scala
// Mill
object test extends Tests {
  def ivyDeps = Agg(ivy"com.github.agourlay::cornichon-test-framework:0.19.5")
  def testFrameworks = Seq("com.github.agourlay.cornichon.framework.CornichonFramework")
}
```