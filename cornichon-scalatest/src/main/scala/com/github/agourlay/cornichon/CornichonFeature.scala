package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.feature.{ BaseFeature, HttpFeature }
import com.github.agourlay.cornichon.scalatest.ScalatestFeature

trait CornichonBaseFeature extends BaseFeature with ScalatestFeature
trait CornichonFeature extends HttpFeature with ScalatestFeature
