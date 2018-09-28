package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

class FeatureToggleExample extends CornichonFeature {

  // the toggle boolean could be loaded from any config object.
  def feature = FeatureToggle("FeatureToggleExample", "Ignored because it hangs!" -> true) {

    Scenario("waiting step") {

      And I wait(1.second)

    }
  }

}
