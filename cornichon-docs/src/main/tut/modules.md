---
layout: docs
title:  "Modules"
position: 6
---

# Modules

The library is composed of several modules with different purposes:

- ```cornichon-core``` : this is the central brick containing the models and execution engine.

- ```cornichon-http-mock``` : contains the ```ListenTo``` DSL and infrastructure to build tests relying on mocked endpoints.

- ```cornichon``` : exposes the cornichon features through an integration with ```Scalatest```. (might be renamed to ```cornichon-scalatest``` later)

- ```cornichon-experimental``` : exposes the cornichon feature through a direct integration with ```SBT test-interface```.

Requires a special configuration in the build.sbt file to use the right `TestFramework`:
`testFrameworks += new TestFramework("com.github.agourlay.cornichon.experimental.sbtinterface.CornichonFramework")`

Moreover `CornichonFeature` lives under `com.github.agourlay.cornichon.experimental`.

- ```cornichon-docs```: documentation built with ```sbt-microsite```.

- ```cornichon-benchmarks``` : performance benchmarks built with ```sbt-jmh```.