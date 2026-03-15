{%
laika.title = Installation
%}

# Installation

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

Next, head over to [Basics](basics.md) to write your first test.
