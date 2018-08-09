package com.github.agourlay.cornichon.util

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine.newBuilder

object Caching {

  def buildCache[K <: Object, V <: Object](size: Long = 1000): Cache[K, V] =
    newBuilder().maximumSize(size).build()

}
