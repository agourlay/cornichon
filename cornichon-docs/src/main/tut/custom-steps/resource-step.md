---
layout: docs
title:  "Resource steps"
---

# ScenarioResourceStep


A `ScenarioResourceStep` is a way to acquire a resource / create some state and make sure that it gets
released / cleaned up at the end of the `Scenario` even if normal control flow is interrupted
(by an error or a failed assertion for example).

It can be implemented by a pair of `Step`s. One to acquire the resource and another to release it.
Information can be communicated between the two steps the same way we would with any other `Step`,
through the `Session`.

So, for example, in a scenario containing:

```scala
Given I setup_some_fixture_data()
```

where
```scala
def setup_some_fixture_data() = ScenarioResourceStep(
  title = "Set up fixture data"
  acquire = EffectStep("insert data", { session =>
    val randomId = insertData()
    session.addValue("id", randomId)
  }),
  release = EffectStep("clean up data", { session =>
    val randomId = session.get("id").right.get
    deleteData(randomId)
  })
)
```

we can be sure that the `clean up data` step runs regardless of what happens after `insert data`.

Multiple `SenarioResourceStep`s are allowed in a `Scenario`. In this case, the `release` `Step`
of the last `ScenarioResourceStep` is run first and we proceed up the `Scenario`.