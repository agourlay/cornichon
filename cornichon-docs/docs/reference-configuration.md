{%
laika.title = Reference configuration
%}

# Reference configuration

It is possible to configure various aspects of the runs via a `/src/test/resources/application.conf` file.

You will find below the available keys and their respective default values.

```
cornichon {
  // timeout for each request
  requestTimeout = 2000 millis
  // base url for all requests
  globalBaseUrl = ""
  // execute scenarios in parallel within a feature
  executeScenariosInParallel = true
  // number of concurrent scenarios = factor * number of CPU + 1
  scenarioExecutionParallelismFactor = 1
  // log trace requests and responses
  traceRequests = false
  // warn if a header is duplicated in a request
  warnOnDuplicateHeaders = false
  // fail if a header is duplicated in a request
  failOnDuplicateHeaders = false
  // add Accept-Encoding: gzip, deflate by default
  addAcceptGzipByDefault = false
  // disable certificate verification for https requests
  disableCertificateVerification = false
  // follow HTTP redirects
  followRedirect = false
  // enable HTTP/2 for requests
  enableHttp2 = false
}
``` 
