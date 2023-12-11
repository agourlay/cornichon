package com.github.agourlay.cornichon.util

object StringUtils {

  private val arrow = " -> "

  def printArrowPairs(params: Seq[(String, String)]): String = {
    if (params.isEmpty) {
      return ""
    }
    val builder = new StringBuilder()
    printArrowPairsBuilder(params, builder)
    builder.result()
  }

  protected[cornichon] def printArrowPairsBuilder(params: Seq[(String, String)], builder: StringBuilder): Unit = {
    val len = params.length
    var i = 0
    params.foreach {
      case (name, value) =>
        quoteInto(builder, name)
        builder.append(arrow)
        quoteInto(builder, value)
        if (i < len - 1) {
          builder.append(", ")
        }
        i += 1
    }
  }

  //https://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Levenshtein_distance#Scala
  def levenshtein(str1: String, str2: String): Int = {
    def min(nums: Int*): Int = nums.min

    val lenStr1 = str1.length
    val lenStr2 = str2.length

    val d: Array[Array[Int]] = Array.ofDim(lenStr1 + 1, lenStr2 + 1)

    for (i <- 0 to lenStr1) d(i)(0) = i
    for (j <- 0 to lenStr2) d(0)(j) = j

    for (i <- 1 to lenStr1; j <- 1 to lenStr2) {
      val cost = if (str1(i - 1) == str2(j - 1)) 0 else 1

      d(i)(j) = min(
        d(i - 1)(j) + 1, // deletion
        d(i)(j - 1) + 1, // insertion
        d(i - 1)(j - 1) + cost // substitution
      )
    }

    d(lenStr1)(lenStr2)
  }

  /**
   * Replace sequentially each pattern in the input string with the corresponding value.
   *
   * This method expects all patterns to be contained in the input string.
   *
   * The goals are:
   * - avoid multiple passes on the input string
   * - avoid creating intermediate strings
   */
  def replacePatternsInOrder(inString: String, pattern_and_replacements: Vector[(String, String)]): String = {
    val patternsLen = pattern_and_replacements.length
    if (patternsLen == 0) return inString
    // assume a similar length for the output string
    val sb = new java.lang.StringBuilder(inString.length)
    // our position in the old string
    var pos = 0
    var i = 0
    while (i < patternsLen) {
      val next = pattern_and_replacements(i)
      val (pattern, newValue) = next
      val index = inString.indexOf(pattern, pos)
      if (index == -1) {
        // this should never happen by contract
        throw new IllegalArgumentException(s"pattern '$pattern' not found in input string '$inString'")
      }
      // append any characters to the left of a match
      sb.append(inString, pos, index)
      // append the new value
      sb.append(newValue)
      pos = index + pattern.length
      i += 1
    }
    // append any characters to the right of a match
    sb.append(inString, pos, inString.length)
    sb.toString
  }

  // add single quoted input into builder
  def quoteInto(builder: StringBuilder, input: String): Unit = {
    builder.append('\'')
    builder.append(input)
    builder.append('\'')
  }

}
