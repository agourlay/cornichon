package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.HttpDsl
import com.github.agourlay.cornichon.http.HttpFeature

trait CornichonFeature extends ScalatestIntegration with HttpDsl with HttpFeature with Feature {}
