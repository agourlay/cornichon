package parsers

import com.github.agourlay.cornichon.core.{RandomContext, Session}
import com.github.agourlay.cornichon.resolver.{Mapper, PlaceholderResolver}
import io.circe.Json
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(
  value = 1,
  jvmArgsAppend = Array("-Xmx1G")
)
class PlaceholderResolvableBench {

  // sbt:benchmarks> jmh:run .*PlaceholderResolvable.* -prof gc -foe true -rf csv

  private val session = Session.newEmpty.addValueUnsafe("hero", "Batman")
  private val rc = RandomContext.fromSeed(1L)
  private val noExtractor = Map.empty[String, Mapper]

  private val noPlaceholderJson: Json = io.circe.parser
    .parse("""
    {
      "superheroes": [
        { "name": "Batman", "city": "Gotham", "hasSuperpowers": false, "publisher": { "name": "DC", "foundationYear": 1934 } },
        { "name": "Superman", "city": "Metropolis", "hasSuperpowers": true, "publisher": { "name": "DC", "foundationYear": 1934 } },
        { "name": "Spider-Man", "city": "New York", "hasSuperpowers": true, "publisher": { "name": "Marvel", "foundationYear": 1939 } },
        { "name": "Wolverine", "city": "Westchester", "hasSuperpowers": true, "publisher": { "name": "Marvel", "foundationYear": 1939 } }
      ],
      "country": "USA",
      "checkedAt": "2026-04-19T12:00:00Z"
    }
  """)
    .toOption
    .get

  private val onePlaceholderJson: Json = io.circe.parser
    .parse("""
    {
      "superheroes": [
        { "name": "<hero>", "city": "Gotham", "hasSuperpowers": false, "publisher": { "name": "DC", "foundationYear": 1934 } },
        { "name": "Superman", "city": "Metropolis", "hasSuperpowers": true, "publisher": { "name": "DC", "foundationYear": 1934 } },
        { "name": "Spider-Man", "city": "New York", "hasSuperpowers": true, "publisher": { "name": "Marvel", "foundationYear": 1939 } },
        { "name": "Wolverine", "city": "Westchester", "hasSuperpowers": true, "publisher": { "name": "Marvel", "foundationYear": 1939 } }
      ],
      "country": "USA",
      "checkedAt": "2026-04-19T12:00:00Z"
    }
  """)
    .toOption
    .get

  // Larger payload: replicate the small body 50x so it's roughly ~35 KB serialized.
  private val largeNoPlaceholderJson: Json =
    Json.obj("items" -> Json.fromValues(Vector.fill(50)(noPlaceholderJson)))

  private val largeOnePlaceholderJson: Json =
    Json.obj("items" -> (Json.fromValues(Vector.fill(49)(noPlaceholderJson) :+ onePlaceholderJson)))

  @Benchmark
  def fillPlaceholdersResolvableJsonNoPlaceholder(): Unit = {
    val res = PlaceholderResolver.fillPlaceholdersResolvable(noPlaceholderJson)(session, rc, noExtractor)
    assert(res.isRight)
  }

  @Benchmark
  def fillPlaceholdersResolvableJsonOnePlaceholder(): Unit = {
    val res = PlaceholderResolver.fillPlaceholdersResolvable(onePlaceholderJson)(session, rc, noExtractor)
    assert(res.isRight)
  }

  @Benchmark
  def fillPlaceholdersResolvableJsonLargeNoPlaceholder(): Unit = {
    val res = PlaceholderResolver.fillPlaceholdersResolvable(largeNoPlaceholderJson)(session, rc, noExtractor)
    assert(res.isRight)
  }

  @Benchmark
  def fillPlaceholdersResolvableJsonLargeOnePlaceholder(): Unit = {
    val res = PlaceholderResolver.fillPlaceholdersResolvable(largeOnePlaceholderJson)(session, rc, noExtractor)
    assert(res.isRight)
  }

}
