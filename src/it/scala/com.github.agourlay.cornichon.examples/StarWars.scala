package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature

// see https://swapi.co/
class StarWars extends CornichonFeature {

  def feature =
    Feature("Star Wars API") {

      Scenario("check out Luke Skywalker") {

        When I get("http://swapi.co/api/people/1/")

        Then assert status.is(200)

        Then assert body.is(
          """
          {
            "name": "Luke Skywalker",
            "height": "172",
            "mass": "77",
            "hair_color": "blond",
            "skin_color": "fair",
            "eye_color": "blue",
            "birth_year": "19BBY",
            "gender": "male",
            "homeworld": "http://swapi.co/api/planets/1/",
            "films": [
              "http://swapi.co/api/films/6/",
              "http://swapi.co/api/films/3/",
              "http://swapi.co/api/films/2/",
              "http://swapi.co/api/films/1/",
              "http://swapi.co/api/films/7/"
            ],
            "species": [
              "http://swapi.co/api/species/1/"
            ],
            "vehicles": [
              "http://swapi.co/api/vehicles/14/",
              "http://swapi.co/api/vehicles/30/"
            ],
            "starships": [
              "http://swapi.co/api/starships/12/",
              "http://swapi.co/api/starships/22/"
            ],
            "created": "2014-12-09T13:50:51.644000Z",
            "edited": "2014-12-20T21:17:56.891000Z",
            "url": "http://swapi.co/api/people/1/"
          }
          """
        )

        And I save_body_path("homeworld" -> "homeworld-url")

        When I get("<homeworld-url>")

        Then assert body.is(
          """
           {
             "name" : "Tatooine",
             "rotation_period" : "23",
             "orbital_period" : "304",
             "diameter" : "10465",
             "climate" : "arid",
             "gravity" : "1 standard",
             "terrain" : "desert",
             "surface_water" : "1",
             "population" : "200000",
             "residents" : [
               "http://swapi.co/api/people/1/",
               "http://swapi.co/api/people/2/",
               "http://swapi.co/api/people/4/",
               "http://swapi.co/api/people/6/",
               "http://swapi.co/api/people/7/",
               "http://swapi.co/api/people/8/",
               "http://swapi.co/api/people/9/",
               "http://swapi.co/api/people/11/",
               "http://swapi.co/api/people/43/",
               "http://swapi.co/api/people/62/"
             ],
             "films" : [
               "http://swapi.co/api/films/5/",
               "http://swapi.co/api/films/4/",
               "http://swapi.co/api/films/6/",
               "http://swapi.co/api/films/3/",
               "http://swapi.co/api/films/1/"
             ],
             "created" : "2014-12-09T13:50:49.641000Z",
             "edited" : "2014-12-21T20:48:04.175778Z",
             "url" : "http://swapi.co/api/planets/1/"
           }
          """
        )

        And I save_body_path("residents[0]" -> "first-resident")

        When I get("<first-resident>")

        Then assert body.path("name").is("Luke Skywalker")
      }
    }

}
