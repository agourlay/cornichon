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

    "acquire multiple resources and release them all before the end of the run even if something blows up in the middle" in {
      val createQueue1 = EffectStep("create the queue1", s ⇒ F(Right(s.addValue("the-queue-1", queueResource.create()))))
      val deleteQueue1 = EffectStep("delete the queue1", s ⇒ F(Right { queueResource.delete(s.get("the-queue-1").right.get); s }))
      val createQueue2 = EffectStep("create the queue2", s ⇒ F(Right(s.addValue("the-queue-2", queueResource.create()))))
      val deleteQueue2 = EffectStep("delete the queue2", s ⇒ F(Right { queueResource.delete(s.get("the-queue-2").right.get); s }))
      val resourceStep1 = ResourceStep("ensure queue exists", createQueue1, deleteQueue1)
      val resourceStep2 = ResourceStep("ensure queue exists", createQueue2, deleteQueue2)
      val fail1 = EffectStep("go boom", _ ⇒ F(Left(BasicError("sooo basic"))))
      val fail2 = EffectStep("go boom", _ ⇒ F(Left(BasicError("i can't even"))))
      val s = Scenario("resource step scenario", resourceStep1 :: fail1 :: resourceStep2 :: fail2 :: Nil)
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
