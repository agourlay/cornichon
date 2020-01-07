---
layout: docs
title:  "Basics"
position: 2
---

# Basics

A Cornichon test is the definition of a so-called `feature`.

Concretely it is a class extending `CornichonFeature` and implementing the required `feature` function.

In the case of `SBT`, those classes live inside `src/test/scala` and can be run them using `sbt test`.

A `feature` can have several `scenarios` which in turn can have several `steps`.

The example below contains one `feature` with one `scenario` with two `steps`.


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

The failure modes are the following:


- A `feature` fails if one or more `scenarios` fail.

- A `scenario` fails if at least one `step` fails.

- A `scenario` will stop at the first failed step encountered and ignore the remaining `steps`.

