package com.github.agourlay.cornichon.util

object StringUtils {

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
   * Replace all occurrences of a substring within a string with another string.
   *
   * credit:
   *  - https://github.com/spring-projects/spring-framework/blob/main/spring-core/src/main/java/org/springframework/util/StringUtils.java#L423
   *  - https://medium.com/javarevisited/micro-optimizations-in-java-string-replaceall-c6d0edf2ef6
   *
   * @param inString String to examine
   * @param oldPattern String to replace
   * @param newPattern String to insert
   * @return a String with the replacements
   */
  def replace_all(inString: String, oldPattern: String, newPattern: String): String = {
    def hasLength(str: String) = str != null && str.nonEmpty

    if (!hasLength(inString) || !hasLength(oldPattern) || newPattern == null) return inString
    var index = inString.indexOf(oldPattern)
    if (index == -1) { // no occurrence -> can return input as-is
      return inString
    }
    var capacity = inString.length
    if (newPattern.length > oldPattern.length) capacity += 16
    val sb = new java.lang.StringBuilder(capacity)
    var pos = 0 // our position in the old string
    val patLen = oldPattern.length
    while (index >= 0) {
      sb.append(inString, pos, index)
      sb.append(newPattern)
      pos = index + patLen
      index = inString.indexOf(oldPattern, pos)
    }
    // append any characters to the right of a match
    sb.append(inString, pos, inString.length)
    sb.toString
  }

}
