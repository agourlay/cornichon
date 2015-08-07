package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.http.HttpFeature
import com.github.agourlay.cornichon.http.dsl.HttpDsl

trait CornichonFeature extends ScalatestIntegration with HttpDsl with HttpFeature with Feature {}
