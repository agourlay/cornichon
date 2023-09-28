---
layout: docs
title:  "Reference configuration"
position: 8
---

# Reference configuration

It is possible to configure various aspects of the runs via a `/src/test/resources/reference.conf` file.

You will find below the available keys and their respective default values.

```
cornichon {
  requestTimeout = 2000 millis                    // timeout for each request
  globalBaseUrl = ""                              // base url for all requests
  executeScenariosInParallel = true               // execute scenarios in parallel within a feature
  scenarioExecutionParallelismFactor = 1          // number of concurrent scenarios = `scenarioExecutionParallelismFactor` * number of CPU + 1
  traceRequests = false                           // log trace requests and responses
  warnOnDuplicateHeaders = false                  // warn if a header is duplicated in a request
  failOnDuplicateHeaders = false                  // fail if a header is duplicated in a request
  disableCertificateVerification = false          // disable certificate verification for https requests
  followRedirect = false,                         // follow Http redirects
  enableHttp2 = false                             // enable Http2 for requests
}
``` 
