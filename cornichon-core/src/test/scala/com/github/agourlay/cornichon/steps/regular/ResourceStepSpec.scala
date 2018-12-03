package com.github.agourlay.cornichon.steps.regular

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.Future
import scala.util.Random

class ResourceStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {
  def randomName = Random.alphanumeric.take(10).mkString
  import QueueManager._

  "ResourceStep" must {
    "acquire a resource and release it before the end of the run even if something blows up in the middle" in {
      implicit val queueResource = new QueueManager
      val resourceStep = ResourceStep(
        "ensure queue exists",
        createAndStoreQueueInSession("the-queue"),
        deleteQueue("the-queue")
      )
      val scenario = Scenario("", resourceStep :: fail :: Nil)

      val run = engine.runScenario(Session.newEmpty)(scenario)

      run.map { report ⇒
        val qName = report.session.get("the-queue").right.get
        queueResource.actionsFor(qName) should be(List(CreateQueue(qName), DeleteQueue(qName)))
        report should not be 'success
        report.logs.toList should contain(InfoLogInstruction("cleanup steps", 2))
        report.logs.find { case SuccessLogInstruction("delete the queue: the-queue", _, _) ⇒ true; case _ ⇒ false } should be('defined)
      }
    }

    "not run a ResourceStep if a previous step failed but should still clean up the resource steps that did run" in {
      implicit val queueResource = new QueueManager
      val resourceStep1 = ResourceStep("ensure q1 exists", createAndStoreQueueInSession("q1"), deleteQueue("q1"))
      val resourceStep2 = ResourceStep("ensure q2 exists", createAndStoreQueueInSession("q2"), deleteQueue("q2"))
      val scenario = Scenario("resource step scenario", resourceStep1 :: fail :: resourceStep2 :: Nil)

      val run = engine.runScenario(Session.newEmpty)(scenario)

      run.map { rep ⇒
        val q1 = rep.session.get("q1").right.get
        queueResource.actionsFor(q1) should be(List(CreateQueue(q1), DeleteQueue(q1)))
      }
    }

    "run all the clean up steps in order" in {
      val is = List.range(1, 5)
      implicit val queueResource = new QueueManager
      val resourceSteps = is.map(i ⇒ ResourceStep(s"ensure q$i exists", createAndStoreQueueInSession(s"q$i"), deleteQueue(s"q$i")))
      val scenario = Scenario("resource step scenario", resourceSteps)

      val run = engine.runScenario(Session.newEmpty)(scenario)

      run.map { rep ⇒
        def q(i: Int) = rep.session.get(s"q$i").right.get
        queueResource.allActions should be(is.map(i ⇒ CreateQueue(q(i))) ++ is.reverse.map(i ⇒ DeleteQueue(q(i))))
      }
    }

    "perform all the release steps even if one fails and report all the ones that failed" in {
      implicit val queueResource = new QueueManager
      val resourceStep1 = ResourceStep("ensure q1 exists", createAndStoreQueueInSession("q1"), deleteQueue("q1"))
      val resourceStep2 = ResourceStep("ensure q2 exists", createAndStoreQueueInSession("q2"), failToDeleteQueue("q2"))
      val resourceStep3 = ResourceStep("ensure q3 exists", createAndStoreQueueInSession("q3"), failToDeleteQueue("q3"))
      val scenario = Scenario("resource step scenario", resourceStep1 :: resourceStep2 :: resourceStep3 :: Nil)

      val run = engine.runScenario(Session.newEmpty)(scenario)

      run.map { rep ⇒
        val q1 = rep.session.get("q1").right.get
        queueResource.actionsFor(q1) should be(List(CreateQueue(q1), DeleteQueue(q1)))
        rep.logs.find { case FailureLogInstruction("fail to delete the queue: q2 *** FAILED ***", _, _) ⇒ true; case _ ⇒ false } should be('defined)
        rep.logs.find { case FailureLogInstruction("fail to delete the queue: q3 *** FAILED ***", _, _) ⇒ true; case _ ⇒ false } should be('defined)
      }
    }
  }

  private def createAndStoreQueueInSession(key: String)(implicit queueResource: QueueManager) =
    EffectStep(
      s"create the queue: $key",
      s ⇒ F {
        val name = key + "-" + randomName
        queueResource.create(name)
        s.addValue(key, name)
      }
    )

  private def deleteQueue(key: String)(implicit queueResource: QueueManager) =
    EffectStep(
      s"delete the queue: $key",
      s ⇒ Future {
        Thread.sleep(Random.nextInt(500).toLong) // To prove that steps are executed in sequence, it's useful to have them take varying amounts of time (so, if they were being executed in parallel the order would be non-deterministic)
        queueResource.delete(s.get(key).right.get)
        Right(s)
      }
    )

  private def failToDeleteQueue(key: String) =
    EffectStep(
      s"fail to delete the queue: $key",
      _ ⇒ Future.successful(Left(BasicError("no queue for you")))
    )

  val fail = EffectStep("go boom", _ ⇒ F(Left(BasicError("sooo basic"))))

  private def F[T] = Future.successful[T] _

  class QueueManager {
    private val state = new AtomicReference[List[Action]](Nil)
    def create(name: String): Unit = state.getAndUpdate(CreateQueue(name) :: (_: List[Action]))
    def delete(name: String): Unit = state.getAndUpdate(DeleteQueue(name) :: (_: List[Action]))
    def actionsFor(name: String) = state.get().collect {
      case a @ CreateQueue(`name`) ⇒ a
      case a @ DeleteQueue(`name`) ⇒ a
    }.reverse
    def allActions = state.get().reverse
  }
  object QueueManager {
    sealed trait Action
    case class CreateQueue(name: String) extends Action
    case class DeleteQueue(name: String) extends Action
    implicit def fnToUnaryOp[A](f: A ⇒ A): UnaryOperator[A] = new UnaryOperator[A] {
      def apply(t: A) = f(t)
    }
  }

}
