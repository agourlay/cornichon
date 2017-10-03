package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature

// see https://swapi.co/
class StarWars extends CornichonFeature {

  def feature =
    Feature("Star Wars API") {

      Scenario("check out Luke Skywalker") {

        When I get("https://swapi.co/api/people/1/")

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
            "homeworld": "https://swapi.co/api/planets/1/",
            "films": [
              "https://swapi.co/api/films/2/",
              "https://swapi.co/api/films/6/",
              "https://swapi.co/api/films/3/",
              "https://swapi.co/api/films/1/",
              "https://swapi.co/api/films/7/"
            ],
            "species": [
              "https://swapi.co/api/species/1/"
            ],
            "vehicles": [
              "https://swapi.co/api/vehicles/14/",
              "https://swapi.co/api/vehicles/30/"
            ],
            "starships": [
              "https://swapi.co/api/starships/12/",
              "https://swapi.co/api/starships/22/"
            ],
            "created": "2014-12-09T13:50:51.644000Z",
            "edited": "2014-12-20T21:17:56.891000Z",
            "url": "https://swapi.co/api/people/1/"
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
               "https://swapi.co/api/people/1/",
               "https://swapi.co/api/people/2/",
               "https://swapi.co/api/people/4/",
               "https://swapi.co/api/people/6/",
               "https://swapi.co/api/people/7/",
               "https://swapi.co/api/people/8/",
               "https://swapi.co/api/people/9/",
               "https://swapi.co/api/people/11/",
               "https://swapi.co/api/people/43/",
               "https://swapi.co/api/people/62/"
             ],
             "films" : [
               "https://swapi.co/api/films/5/",
               "https://swapi.co/api/films/4/",
               "https://swapi.co/api/films/6/",
               "https://swapi.co/api/films/3/",
               "https://swapi.co/api/films/1/"
             ],
             "created" : "2014-12-09T13:50:49.641000Z",
             "edited" : "2014-12-21T20:48:04.175778Z",
             "url" : "https://swapi.co/api/planets/1/"
           }
          """
        )

        And I save_body_path("residents[0]" -> "first-resident")

        When I get("<first-resident>")

        Then assert body.path("name").is("Luke Skywalker")
      }
    }

}
