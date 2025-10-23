package com.github.agourlay.cornichon.json

import cats.{ Eq, Show }
import cats.data.Chain
import cats.syntax.show._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, Session }
import com.github.agourlay.cornichon.dsl.DataTableParser
import diffson._
import diffson.lcs._
import diffson.circe._
import diffson.jsonpatch._
import diffson.jsonpatch.lcsdiff.remembering._
import diffson.jsonpointer.{ Part, Pointer }
import io.circe._
import io.circe.syntax._
import sangria.marshalling.MarshallingUtil._
import sangria.parser.QueryParser
import sangria.marshalling.queryAst._
import sangria.marshalling.circe._

import scala.annotation.switch
import scala.collection.compat.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Success, Try }

trait CornichonJson {

  // A DSL String can be :
  // - an object
  // - an array
  // - a string
  // - a data table
  private def parseDslStringJson(s: String): Either[CornichonError, Json] =
    firstNonEmptyChar(s) match {
      case None => Right(Json.fromString(s))
      case Some(firstChar) =>
        (firstChar: @switch) match {
          // parse object or array
          case '{' | '[' =>
            parseString(s)
          // table is turned into a JArray
          case '|' =>
            parseDataTableJson(s)
          // treated as a JString
          case _ =>
            Right(Json.fromString(s))
        }
    }

  def parseDslJson[A: Encoder: Show](input: A): Either[CornichonError, Json] = input match {
    case s: String => parseDslStringJson(s)
    case _         => Either.catchNonFatal(input.asJson).leftMap(f => MalformedJsonError(input.show, f.getMessage))
  }

  def parseDslJsonUnsafe[A: Encoder: Show](input: A): Json =
    parseDslJson(input).valueUnsafe

  def parseString(s: String): Either[MalformedJsonError[String], Json] =
    io.circe.parser.parse(s).leftMap(f => MalformedJsonError(s, f.message))

  def isJsonString(s: String): Boolean =
    firstNonEmptyChar(s) match {
      case None       => false
      case Some(head) => head != '[' && head != '{' && head != '|'
    }

  private def firstNonEmptyChar(s: String): Option[Char] = {
    val len = s.length
    var i = 0
    while (i < len) {
      val ch = s.charAt(i)
      if (!ch.isWhitespace) return Some(ch)
      i += 1
    }
    None
  }

  private def parseDataTableRow(rawRow: List[(String, String)]): Either[MalformedJsonError[String], JsonObject] = {
    val cells = ArraySeq.newBuilder[(String, Json)]
    cells.sizeHint(rawRow.length)
    rawRow.foreach {
      case (name, rawValue) =>
        parseString(rawValue) match {
          case Right(json) => cells += (name -> json)
          case Left(e)     => return Left(e)
        }
    }
    // `fromIterable` is faster than `fromMap`
    Right(JsonObject.fromIterable(cells.result()))
  }

  private def parseDataTableJson(table: String): Either[CornichonError, Json] = {
    parseDataTableRaw(table).map { rawRows =>
      val rows = Vector.newBuilder[Json]
      rawRows.foreach { rawRow =>
        parseDataTableRow(rawRow) match {
          case Right(r) => rows += Json.fromJsonObject(r)
          case Left(e)  => return Left(e)
        }
      }
      // `fromValues` wants a Vector as a concrete type
      Json.fromValues(rows.result())
    }
  }

  def parseDataTable(table: String): Either[CornichonError, List[JsonObject]] = {
    parseDataTableRaw(table).map { rawRows =>
      val rows = new ListBuffer[JsonObject]()
      rawRows.foreach { rawRow =>
        parseDataTableRow(rawRow) match {
          case Right(r) => rows += r
          case Left(e)  => return Left(e)
        }
      }
      rows.result()
    }
  }

  // Returns raw data with duplicates and initial ordering
  def parseDataTableRaw(table: String): Either[CornichonError, List[List[(String, String)]]] = {
    DataTableParser.parse(table).map { dataTable =>
      val rows = dataTable.rows
      val headers = dataTable.headers
      val rowsBuffer = new ListBuffer[List[(String, String)]]()
      var i = 0
      val rowsLen = rows.length
      while (i < rowsLen) {
        val row = rows(i)
        val fieldsBuffer = new ListBuffer[(String, String)]()
        var j = 0
        val fieldLen = row.fields.length
        while (j < fieldLen) {
          val value = row.fields(j)
          val stripped = value.stripTrailing()
          if (stripped.nonEmpty) {
            val name = headers.fields(j)
            fieldsBuffer += name -> stripped
          }
          j += 1
        }
        i += 1
        rowsBuffer += fieldsBuffer.toList
      }
      rowsBuffer.toList
    }
  }

  def parseGraphQLJson(input: String): Either[MalformedGraphQLJsonError[String], Json] =
    QueryParser.parseInput(input) match {
      case Success(value) => value.convertMarshaled[Json].asRight
      case Failure(e)     => MalformedGraphQLJsonError(input, e).asLeft
    }

  private def jsonArrayValues(json: Json): Either[CornichonError, Vector[Json]] =
    json.asArray match {
      case Some(arr) => Right(arr)
      case None      => Left(NotAnArrayError(json))
    }

  def parseArray(input: String): Either[CornichonError, Vector[Json]] =
    parseDslJson(input).flatMap(jsonArrayValues)

  def selectMandatoryArrayJsonPath(json: String, path: JsonPath): Either[CornichonError, Vector[Json]] =
    path.runStrict(json).flatMap(jsonArrayValues)

  def removeFieldsByPath(input: Json, paths: Seq[JsonPath]): Json = {
    if (paths.isEmpty)
      input
    else
      paths.foldLeft(input) { (json, path) => path.removeFromJson(json) }
  }

  def jsonStringValue(j: Json): String =
    // Use Json.Folder for performance https://github.com/circe/circe/pull/656
    j.foldWith(
      new Json.Folder[String] {
        def onNull: String = ""
        def onBoolean(value: Boolean): String = j.show
        def onNumber(value: JsonNumber): String = j.show
        def onString(value: String): String = value
        def onArray(value: Vector[Json]): String = j.show
        def onObject(value: JsonObject): String = j.show
      }
    )

  def extract(json: Json, path: String): Either[CornichonError, Json] =
    JsonPath.runStrict(path, json)

  def prettyPrint(json: Json): String =
    json.spaces2

  implicit private val lcs: Patience[Json] = new Patience[Json]

  private def pathWithoutIndex(path: Pointer): Chain[Part] = {
    path.parts.map {
      case s @ Left(_) => s
      case Right(_)    => Right(0)
    }
  }

  def diffPatch(first: Json, second: Json, ignoreArrayOrdering: Boolean): JsonPatch[Json] = {
    val allDiffs = diff(first, second)
    if (!ignoreArrayOrdering) {
      allDiffs
    } else {
      val eqJson: Eq[Json] = implicitly
      val newDiffs: List[Operation[Json]] = allDiffs.ops.foldLeft(List.empty[Operation[Json]]) {
        case (acc, e) =>
          e match {
            case a @ Add(path, value) =>
              val aWithoutIndex = pathWithoutIndex(path)
              println(aWithoutIndex)
              val removed = allDiffs.ops.exists {
                case Remove(path, rValue) if pathWithoutIndex(path) == aWithoutIndex && rValue.exists(v => eqJson.eqv(v, value)) => true
                case _ => false
              }
              if (removed) acc else a :: acc

            case r @ Remove(path, value) =>
              value match {
                case None => r :: acc
                case Some(removedValue) =>
                  val rWithoutIndex = pathWithoutIndex(path)
                  val added = allDiffs.ops.exists {
                    case Add(path, aValue) if pathWithoutIndex(path) == rWithoutIndex && eqJson.eqv(aValue, removedValue) => true
                    case _                                                                                                => false
                  }
                  if (added) acc else r :: acc
              }

            case other => other :: acc
          }
      }
      JsonPatch(newDiffs)
    }
  }

  def decodeAs[A: Decoder](json: Json): Either[CornichonError, A] =
    json.as[A].leftMap(df => JsonDecodingFailure(json, df.message))

  // `first` must be a STRICT subset of `second` in terms of keys.
  // Returns `first` populated with the missing keys from `second` or an error.
  // The goal is to perform diffs without providing all the keys.
  def whitelistingValue(first: Json, second: Json, ignoreArrayOrdering: Boolean): Either[CornichonError, Json] = {
    val diffOps = diffPatch(first, second, ignoreArrayOrdering).ops
    val forbiddenPatchOps = diffOps.collect { case r: Remove[Json] => r }
    if (forbiddenPatchOps.isEmpty) {
      val addOps = diffOps.collect { case r: Add[Json] => r }
      JsonPatch(addOps).apply[Try](first).toEither.leftMap(CornichonError.fromThrowable)
    } else
      WhitelistingError(forbiddenPatchOps.map(_.path.show), second).asLeft
  }

  def findAllPathWithValue(values: Set[String], json: Json): List[(String, JsonPath)] = {
    def keyValues(currentPath: String, json: Json, level: Int): List[(String, Json)] = {
      // Use Json.Folder for performance https://github.com/circe/circe/pull/656
      json.foldWith(
        // TODO folder on ListBuffer for performance
        new Json.Folder[List[(String, Json)]] {
          def onNull: List[(String, Json)] =
            Nil
          def onBoolean(value: Boolean): List[(String, Json)] =
            (currentPath -> json) :: Nil
          def onNumber(value: JsonNumber): List[(String, Json)] =
            (currentPath -> json) :: Nil
          def onString(value: String): List[(String, Json)] =
            (currentPath -> json) :: Nil
          def onArray(elems: Vector[Json]): List[(String, Json)] = {
            val listBuffer = new ListBuffer[(String, Json)]
            var index = 0
            val nextLevel = level + 1
            while (index < elems.length) {
              val e = elems(index)
              listBuffer ++= keyValues(s"$currentPath[$index]", e, nextLevel)
              index += 1
            }
            listBuffer.toList
          }
          def onObject(elems: JsonObject): List[(String, Json)] = {
            val listBuffer = new ListBuffer[(String, Json)]
            val nextLevel = level + 1
            for ((k, v) <- elems.toIterable) {
              listBuffer ++= keyValues(s"$currentPath.$k", v, nextLevel)
            }
            listBuffer.toList
          }
        }
      )
    }

    // Do not traverse the JSON if there are no values to find
    if (values.nonEmpty) {
      keyValues(JsonPath.root, json, level = 0).collect {
        case (path, value) if value.asString.exists(values.contains) => (value.asString.get, JsonPath.parse(path).valueUnsafe)
      }
    } else
      Nil
  }
}

object CornichonJson extends CornichonJson {

  implicit class sessionJson(val s: Session) {
    def getJson(key: String, stackingIndex: Option[Int] = None, path: String = JsonPath.root): Either[CornichonError, Json] =
      for {
        sessionValue <- s.get(key, stackingIndex)
        jsonValue <- parseDslJson(sessionValue)
        extracted <- JsonPath.runStrict(path, jsonValue)
      } yield extracted

    def getJsonStringField(key: String, stackingIndex: Option[Int] = None, path: String = JsonPath.root): Either[CornichonError, String] =
      for {
        json <- getJson(key, stackingIndex, path)
        field <- Either.fromOption(json.asString, NotStringFieldError(json, path))
      } yield field

    def getJsonStringFieldUnsafe(key: String, stackingIndex: Option[Int] = None, path: String = JsonPath.root): String =
      getJsonStringField(key, stackingIndex, path).valueUnsafe

    def getJsonOpt(key: String, stackingIndex: Option[Int] = None): Option[Json] =
      s.getOpt(key, stackingIndex).flatMap(s => parseDslJson(s).toOption)
  }

  implicit class GqlHelper(val sc: StringContext) extends AnyVal {
    def gqljson(args: Any*): GqlString = {
      val input = sc.s(args: _*)
      GqlString(input)
    }
  }
}
