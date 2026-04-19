package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.show._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{CornichonError, Session}
import com.github.agourlay.cornichon.dsl.DataTableParser
import diffson._
import diffson.lcs._
import diffson.circe._
import diffson.jsonpatch._
import diffson.jsonpatch.lcsdiff.remembering._
import io.circe._
import io.circe.syntax._
import sangria.marshalling.MarshallingUtil._
import sangria.parser.QueryParser
import sangria.marshalling.queryAst._
import sangria.marshalling.circe._

import scala.annotation.switch
import scala.collection.compat.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

trait CornichonJson {

  // A DSL String can be :
  // - an object
  // - an array
  // - a string
  // - a data table
  //
  // Dispatch is based on the first non-whitespace character:
  // - '{' or '[' → JSON parsing (invalid JSON is an error, not a string fallback)
  // - '|' → data table parsing (requires at least two pipes)
  // - anything else → plain string
  //
  // Known limitation: string values starting with '{', '[', or '|' cannot be
  // represented through this DSL — they will be interpreted as JSON or data tables.
  private def parseDslStringJson(s: String): Either[CornichonError, Json] = {
    val idx = firstNonEmptyCharIdx(s)
    if (idx < 0) Right(Json.fromString(s))
    else
      (s.charAt(idx): @switch) match {
        // parse object or array
        case '{' | '[' =>
          parseString(s)
        // table is turned into a JArray (requires at least two pipes for a valid header row)
        case '|' =>
          if (s.indexOf('|', idx + 1) >= 0) parseDataTableJson(s)
          else Right(Json.fromString(s))
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

  // Pretty-print as JSON if possible, otherwise return the raw string
  def prettyPrintJson(source: String): String =
    parseDslJson(source).fold(_ => source, _.spaces2)

  def parseString(s: String): Either[MalformedJsonError[String], Json] =
    io.circe.parser.parse(s).leftMap(f => MalformedJsonError(s, f.message))

  def isJsonString(s: String): Boolean = {
    val idx = firstNonEmptyCharIdx(s)
    if (idx < 0) false
    else {
      val head = s.charAt(idx)
      head != '[' && head != '{' && head != '|'
    }
  }

  // Returns the index of the first non-whitespace char, or -1 if none.
  // Sentinel-int return avoids Option[Char] allocation on every parse.
  private def firstNonEmptyCharIdx(s: String): Int = {
    val len = s.length
    var i = 0
    while (i < len) {
      if (!s.charAt(i).isWhitespace) return i
      i += 1
    }
    -1
  }

  private def parseDataTableRow(rawRow: List[(String, String)]): Either[MalformedJsonError[String], JsonObject] = {
    val cells = ArraySeq.newBuilder[(String, Json)]
    cells.sizeHint(rawRow.length)
    rawRow.foreach { case (name, rawValue) =>
      parseString(rawValue) match {
        case Right(json) => cells += (name -> json)
        case Left(e)     => return Left(e)
      }
    }
    // `fromIterable` is faster than `fromMap`
    Right(JsonObject.fromIterable(cells.result()))
  }

  private def parseDataTableJson(table: String): Either[CornichonError, Json] =
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

  def parseDataTable(table: String): Either[CornichonError, List[JsonObject]] =
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

  // Returns raw data with duplicates and initial ordering
  def parseDataTableRaw(table: String): Either[CornichonError, List[List[(String, String)]]] =
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

  def removeFieldsByPath(input: Json, paths: Seq[JsonPath]): Json =
    if (paths.isEmpty)
      input
    else
      paths.foldLeft(input)((json, path) => path.removeFromJson(json))

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

  def diffPatch(first: Json, second: Json): JsonPatch[Json] =
    diff(first, second)

  def decodeAs[A: Decoder](json: Json): Either[CornichonError, A] =
    json.as[A].leftMap(df => JsonDecodingFailure(json, df.message))

  // `first` must be a STRICT subset of `second` in terms of keys.
  // Returns `first` populated with the missing keys from `second` or an error.
  // The goal is to perform diffs without providing all the keys.
  def whitelistingValue(first: Json, second: Json): Either[CornichonError, Json] = {
    val diffOps = diffPatch(first, second).ops
    val forbiddenPatchOps = diffOps.collect { case r: Remove[Json] => r }
    if (forbiddenPatchOps.isEmpty) {
      val addOps = diffOps.collect { case r: Add[Json] => r }
      JsonPatch(addOps).apply[Try](first).toEither.leftMap(CornichonError.fromThrowable)
    } else
      WhitelistingError(forbiddenPatchOps.map(_.path.show), second).asLeft
  }

  def findAllPathWithStringValue(values: Set[String], json: Json): List[(String, JsonPath)] = {
    // Build JsonPath operations inline during traversal: no string concat, no re-parsing.
    // Also handles keys containing '.', '[', ']' and array-of-arrays correctly — the string form would not round-trip.
    def descendField(ops: Vector[JsonPathOperation], field: String): Vector[JsonPathOperation] =
      if (ops.isEmpty) Vector(RootSelection, FieldSelection(field))
      else ops :+ FieldSelection(field)

    def descendIndex(ops: Vector[JsonPathOperation], idx: Int): Vector[JsonPathOperation] =
      if (ops.isEmpty) Vector(RootArrayElementSelection(idx))
      else
        ops.last match {
          case FieldSelection(f) => ops.init :+ ArrayFieldSelection(f, idx)
          case _                 => ops :+ RootArrayElementSelection(idx)
        }

    def keyValues(ops: Vector[JsonPathOperation], json: Json, acc: ListBuffer[(String, JsonPath)]): Unit =
      // Use Json.Folder for performance https://github.com/circe/circe/pull/656
      json.foldWith(
        new Json.Folder[Unit] {
          def onNull: Unit = ()
          def onBoolean(value: Boolean): Unit = ()
          def onNumber(value: JsonNumber): Unit = ()
          def onString(value: String): Unit =
            if (values.contains(value))
              acc += (value -> JsonPath(ops))
          def onArray(elems: Vector[Json]): Unit = {
            var index = 0
            while (index < elems.length) {
              keyValues(descendIndex(ops, index), elems(index), acc)
              index += 1
            }
          }
          def onObject(elems: JsonObject): Unit =
            for ((k, v) <- elems.toIterable)
              keyValues(descendField(ops, k), v, acc)
        }
      )

    // Do not traverse the JSON if there are no values to find
    if (values.nonEmpty) {
      val acc = new ListBuffer[(String, JsonPath)]
      keyValues(Vector.empty, json, acc)
      acc.result()
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
