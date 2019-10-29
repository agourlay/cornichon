package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.util.ScenarioMatchers

import utest._

import scala.concurrent.Future
import scala.util.Random

object ScenarioResourceStepSpec extends TestSuite with StepUtilSpec with ScenarioMatchers {
  def randomName: String = Random.alphanumeric.take(10).mkString
  import QueueManager._

  val tests = Tests {
    test("acquire a resource and release it before the end of the run even if something blows up in the middle") {
      implicit val queueResource = new QueueManager
      val resourceStep = ScenarioResourceStep(
        "ensure queue exists",
        createAndStoreQueueInSession("the-queue"),
        deleteQueue("the-queue")
      )
      val scenario = Scenario("", resourceStep :: fail :: Nil)

      val report = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(scenario))
      val qName = report.session.get("the-queue").right.get
      assert(queueResource.actionsFor(qName) == List(CreateQueue(qName), DeleteQueue(qName)))
      assert(report.logs.contains(InfoLogInstruction("cleanup steps", 2)))
      assert(report.logs.exists { case SuccessLogInstruction("delete the queue: the-queue", _, _) => true; case _ => false })
    }

    test("not run a ResourceStep if a previous step failed but should still clean up the resource steps that did run") {
      implicit val queueResource = new QueueManager
      val resourceStep1 = ScenarioResourceStep("ensure q1 exists", createAndStoreQueueInSession("q1"), deleteQueue("q1"))
      val resourceStep2 = ScenarioResourceStep("ensure q2 exists", createAndStoreQueueInSession("q2"), deleteQueue("q2"))
      val scenario = Scenario("resource step scenario", resourceStep1 :: fail :: resourceStep2 :: Nil)

      val rep = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(scenario))
      val q1 = rep.session.get("q1").right.get
      assert(queueResource.actionsFor(q1) == List(CreateQueue(q1), DeleteQueue(q1)))
    }

    test("runs all the clean up steps in order") {
      val is = List.range(1, 5)
      implicit val queueResource = new QueueManager
      val resourceSteps = is.map(i => ScenarioResourceStep(s"ensure q$i exists", createAndStoreQueueInSession(s"q$i"), deleteQueue(s"q$i")))
      val scenario = Scenario("resource step scenario", resourceSteps)

      val rep = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(scenario))
      def q(i: Int) = rep.session.get(s"q$i").right.get
      assert(queueResource.allActions == is.map(i => CreateQueue(q(i))) ++ is.reverse.map(i => DeleteQueue(q(i))))
    }

    test("perform all the release steps even if one fails and report all the ones that failed") {
      implicit val queueResource = new QueueManager
      val resourceStep1 = ScenarioResourceStep("ensure q1 exists", createAndStoreQueueInSession("q1"), deleteQueue("q1"))
      val resourceStep2 = ScenarioResourceStep("ensure q2 exists", createAndStoreQueueInSession("q2"), failToDeleteQueue("q2"))
      val resourceStep3 = ScenarioResourceStep("ensure q3 exists", createAndStoreQueueInSession("q3"), failToDeleteQueue("q3"))
      val scenario = Scenario("resource step scenario", resourceStep1 :: resourceStep2 :: resourceStep3 :: Nil)

      val rep = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(scenario))
      val q1 = rep.session.get("q1").right.get
      assert(queueResource.actionsFor(q1) == List(CreateQueue(q1), DeleteQueue(q1)))
      scenarioFailsWithMessage(rep) {
        """Scenario 'resource step scenario' failed:
            |
            |at step:
            |fail to delete the queue: q2
            |
            |with error(s):
            |no queue for you
            |
            |and
            |
            |at step:
            |fail to delete the queue: q3
            |
            |with error(s):
            |no queue for you
            |
            |seed for the run was '1'
            |""".stripMargin
      }
    }
  }

  private def createAndStoreQueueInSession(key: String)(implicit queueResource: QueueManager) =
    EffectStep(
      s"create the queue: $key",
      sc => F {
        val name = key + "-" + randomName
        queueResource.create(name)
        sc.session.addValue(key, name)
      }
    )

  private def deleteQueue(key: String)(implicit queueResource: QueueManager) =
    EffectStep(
      s"delete the queue: $key",
      sc => Future {
        Thread.sleep(Random.nextInt(500).toLong) // To prove that steps are executed in sequence, it's useful to have them take varying amounts of time (so, if they were being executed in parallel the order would be non-deterministic)
        queueResource.delete(sc.session.get(key).right.get)
        Right(sc.session)
      }
    )

  private def failToDeleteQueue(key: String) =
    EffectStep(
      s"fail to delete the queue: $key",
      _ => Future.successful(Left(BasicError("no queue for you")))
    )

  val fail = EffectStep("go boom", _ => F(Left(BasicError("sooo basic"))))

  private def F[T] = Future.successful[T] _

  class QueueManager {
    private val state = new AtomicReference[List[Action]](Nil)
    def create(name: String): Unit = state.getAndUpdate(CreateQueue(name) :: (_: List[Action]))
    def delete(name: String): Unit = state.getAndUpdate(DeleteQueue(name) :: (_: List[Action]))
    def actionsFor(name: String) = state.get().collect {
      case a @ CreateQueue(`name`) => a
      case a @ DeleteQueue(`name`) => a
    }.reverse
    def allActions = state.get().reverse
  }
  object QueueManager {
    sealed trait Action
    case class CreateQueue(name: String) extends Action
    case class DeleteQueue(name: String) extends Action
    implicit def fnToUnaryOp[A](f: A => A): UnaryOperator[A] = new UnaryOperator[A] {
      def apply(t: A) = f(t)
    }
  }
}
