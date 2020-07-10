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
  requestTimeout = 2000 millis
  globalBaseUrl = ""
  executeScenariosInParallel = true
  scenarioExecutionParallelismFactor = 1
  traceRequests = false 
  warnOnDuplicateHeaders = false
  failOnDuplicateHeaders = false
  disableCertificateVerification = false
  followRedirect = false
}
``` 
