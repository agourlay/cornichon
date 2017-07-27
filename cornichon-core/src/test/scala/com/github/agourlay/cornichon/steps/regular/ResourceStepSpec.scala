package com.github.agourlay.cornichon.steps.regular

import java.util.concurrent.ConcurrentSkipListSet

import com.github.agourlay.cornichon.core.{ BasicError, Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.Future
import scala.util.Random

class ResourceStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {
  "ResourceStep" must {
    "acquire a resource and release it before the end of the run even if something blows up in the middle" in {
      val createQueue = EffectStep("create the queue", s ⇒ F(Right(s.addValue("the-queue", queueResource.create()))))
      val deleteQueue = EffectStep("delete the queue", s ⇒ F(Right { queueResource.delete(s.get("the-queue").right.get); s }))
      val resourceStep = ResourceStep("ensure queue exists", createQueue, deleteQueue)
      val fail = EffectStep("go boom", _ ⇒ F(Left(BasicError("sooo basic"))))
      val s = Scenario("resource step scenario", resourceStep :: fail :: Nil)
      engine.runScenario(Session.newEmpty)(s).map(_ ⇒ queueResource should be('empty))
    }
  }

  private def F[T] = Future.successful[T] _
  private val queueResource = new QueueManager

  class QueueManager {
    private val state = new ConcurrentSkipListSet[String]()
    def create(): String = {
      val queueName = Random.alphanumeric.take(10).mkString
      state.add(queueName)
      queueName
    }
    def delete(name: String): Unit = state.remove(name)
    def isEmpty = state.isEmpty
  }

}
