package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.ScenarioUtilSpec
import com.github.agourlay.cornichon.core.Scenario
import com.github.agourlay.cornichon.examples.ExampleServer
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import akka.http.scaladsl.model.StatusCodes._
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

class HttpDslSpec extends WordSpec with Matchers with ScenarioUtilSpec with ExampleServer {

  // use to start Superheroes exampleAPI
  lazy val port = 8080

  val baseUrl = s"http://localhost:$port"

  "HTTP Dsls" must {
    "execute scenarios" in {
      val feature = new DslTest {

        override def featureName: String = "HTTP Dsl"
        override def scenarios: Seq[Scenario] = Seq(
          scenario("Playing with the http DSL")(

            // Simple GET
            When(GET(s"$baseUrl/superheroes/Batman")),

            // Test status of previous request
            Then(status_is(200)),

            // Test body of previous request
            Then(response_body_is(
              """
                |{
                |  "name": "Batman",
                |  "realName": "Bruce Wayne",
                |  "city": "Gotham city",
                |  "publisher": "DC"
                |}
              """.stripMargin.trim)
            ),

            // Provide predicate for response status
            When(GET(s"$baseUrl/superheroes/Batman", _.status == OK)),

            // Provide predicate for response body
            When(GET(s"$baseUrl/superheroes/Batman", _.body ==
              """
                |{
                |  "name": "Batman",
                |  "realName": "Bruce Wayne",
                |  "city": "Gotham city",
                |  "publisher": "DC"
                |}
              """.stripMargin.parseJson
            )),

            // Debug steps printing into console
            Then(showSession),
            Then(showLastStatus),
            Then(showLastReponseJson),

            // Extract from body using lense
            When(GET(s"$baseUrl/superheroes/Batman", _.body.extract[String]('city) == "Gotham city")),

            // It is just a function ;)
            When(GET(s"$baseUrl/superheroes/Batman", r ⇒ r.status == OK &&
              r.body == """
                |{
                |  "name": "Batman",
                |  "realName": "Bruce Wayne",
                |  "city": "Gotham city",
                |  "publisher": "DC"
                |}
              """.stripMargin.parseJson
            )),

            // Set a key/value in the Scenario's session
            Given(Set("favorite-superhero", "Batman")),

            // Retrieve dynamically from session with <key>
            When(GET(s"$baseUrl/superheroes/<favorite-superhero>", _.body ==
              """
                |{
                |  "name": "Batman",
                |  "realName": "Bruce Wayne",
                |  "city": "Gotham city",
                |  "publisher": "DC"
                |}
              """.stripMargin.parseJson
            ))
          )
        )
      }

      val featureReport = feature.runFeature()
      printlnFailedScenario(featureReport)
      featureReport.success should be(true)
    }
  }

}
