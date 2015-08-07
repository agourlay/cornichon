package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.Feature
import com.github.agourlay.cornichon.http.HttpFeature
import com.github.agourlay.cornichon.http.dsl.HttpDsl

// implement this trait for dsl test
trait DslTest extends HttpFeature with HttpDsl with Feature {}
