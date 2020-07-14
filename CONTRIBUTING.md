# Contributing

- for general questions use the [Gitter channel](https://gitter.im/agourlay/cornichon)
- in case of bugs, open issues with code example as reproducer

To propose a patch, fork the repository and use the following commands:
- `sbt test` to run unit tests
- `sbt it:test` to run integration tests interacting with external APIs
- follow those [instructions](https://47degrees.github.io/sbt-microsites/docs/build-the-microsite) to build the documentation locally

A PR should ideally include:
- a clear description of the problem solved and of the solution implemented
- unit tests
- tests example of usage if the PR introduces changes in the DSL (e.g SuperHeroesScenario)
- an update to the documentation if necessary
