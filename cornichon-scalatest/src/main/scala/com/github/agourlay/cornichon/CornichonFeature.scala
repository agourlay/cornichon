package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.dsl.{ BaseFeature, CoreDsl }
import com.github.agourlay.cornichon.http.HttpDsl
import com.github.agourlay.cornichon.json.JsonDsl
import com.github.agourlay.cornichon.scalatest.ScalatestFeature

trait CornichonBaseFeature extends BaseFeature with CoreDsl with ScalatestFeature
trait CornichonFeature extends CornichonBaseFeature with HttpDsl with JsonDsl
