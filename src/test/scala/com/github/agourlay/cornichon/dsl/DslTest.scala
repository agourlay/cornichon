package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.http.HttpFeature

// implement this trait for dsl test
trait DslTest extends HttpFeature with HttpDsl {}
