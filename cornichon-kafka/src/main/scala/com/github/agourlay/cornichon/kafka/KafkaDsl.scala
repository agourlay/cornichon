package com.github.agourlay.cornichon.kafka

import java.time.Duration

import com.github.agourlay.cornichon.core.{ CornichonError, Session, Step }
import com.github.agourlay.cornichon.dsl.{ BaseFeature, CoreDsl }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{ StringDeserializer, StringSerializer }
import monix.eval.Task
import monix.execution.CancelablePromise
import org.apache.kafka.clients.consumer.{ ConsumerConfig, ConsumerRecord, KafkaConsumer }
import cats.syntax.either._

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait KafkaDsl {
  this: BaseFeature with CoreDsl ⇒

  // Kafka scenario can not run in // because they share the same producer/consumer
  override lazy val executeScenariosInParallel: Boolean = false

  lazy val kafkaBootstrapServersHost: String = "localhost"
  lazy val kafkaBootstrapServersPort: Int = 9092
  private lazy val kafkaBootstrapServer = s"$kafkaBootstrapServersHost:$kafkaBootstrapServersPort"

  val kafkaProducerConfig: KafkaProducerConfig = KafkaProducerConfig()
  val kafkaConsumerConfig: KafkaConsumerConfig = KafkaConsumerConfig()

  lazy val featureProducer: KafkaProducer[String, String] = producer(kafkaBootstrapServer, kafkaProducerConfig)
  lazy val featureConsumer: KafkaConsumer[String, String] = consumer(kafkaBootstrapServer, kafkaConsumerConfig)

  def put_topic(topic: String, key: String, message: String): Step = EffectStep.fromAsync(
    title = s"put message=$message with key=$key to topic=$topic",
    effect = sc ⇒ {
      val pr = new ProducerRecord[String, String](topic, key, message)
      val cp = CancelablePromise[Unit]()
      featureProducer.send(pr, new Callback {
        def onCompletion(metadata: RecordMetadata, exception: Exception): Unit =
          if (exception == null)
            cp.success(())
          else
            cp.failure(exception)
      })
      Task.fromCancelablePromise(cp).map(_ ⇒ sc.session)
    }
  )

  def read_from_topic(topic: String, atLeastAmount: Int = 1, timeoutMs: Int = 500): Step = EffectStep[Task](
    title = s"reading the last $atLeastAmount messages from topic=$topic",
    effect = sc ⇒ Task.delay {
      featureConsumer.subscribe(Seq(topic).asJava)
      val messages = ListBuffer.empty[ConsumerRecord[String, String]]
      var nothingNewAnymore = false
      val pollDuration = Duration.ofMillis(timeoutMs.toLong)
      while (!nothingNewAnymore) {
        val newMessages = featureConsumer.poll(pollDuration)
        if (newMessages.isEmpty)
          nothingNewAnymore = true
        else
          messages ++= newMessages.iterator().asScala.toList
      }
      featureConsumer.commitSync()
      if (messages.size < atLeastAmount)
        NotEnoughMessagesPolled(atLeastAmount, messages.toList).asLeft
      else {
        messages.drop(messages.size - atLeastAmount)
        val newSession = messages.foldLeft(sc.session) { (session, value) ⇒
          commonSessionExtraction(session, topic, value).valueUnsafe
        }
        newSession.asRight
      }
    }
  )

  def kafka(topic: String) = KafkaStepBuilder(sessionKey = topic)

  private def commonSessionExtraction(session: Session, topic: String, response: ConsumerRecord[String, String]) =
    session.addValues(
      s"$topic-topic" → response.topic(),
      s"$topic-key" → response.key(),
      s"$topic-value" → response.value()
    )

  // the producer is stopped after all features
  private def producer(bootstrapServer: String, producerConfig: KafkaProducerConfig): KafkaProducer[String, String] = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> bootstrapServer,
      ProducerConfig.ACKS_CONFIG -> producerConfig.ack,
      ProducerConfig.BATCH_SIZE_CONFIG -> producerConfig.batchSizeInBytes.toString
    )

    val p = new KafkaProducer[String, String](configMap.asJava, new StringSerializer, new StringSerializer)
    BaseFeature.addShutdownHook(() ⇒ Future {
      p.close()
    })
    p
  }

  // the consumer is stopped after all features
  private def consumer(bootstrapServer: String, consumerConfig: KafkaConsumerConfig): KafkaConsumer[String, String] = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> bootstrapServer,
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false",
      ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG -> consumerConfig.heartbeatIntervalMsConfig.toString,
      ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG -> consumerConfig.sessionTimeoutMsConfig.toString,
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.GROUP_ID_CONFIG -> consumerConfig.groupId
    )

    val c = new KafkaConsumer[String, String](configMap.asJava, new StringDeserializer, new StringDeserializer)
    BaseFeature.addShutdownHook(() ⇒ Future {
      c.close()
    })
    c
  }
}

case class KafkaProducerConfig(ack: String = "all", batchSizeInBytes: Int = 1, retriesConfig: Option[Int] = None)

case class KafkaConsumerConfig(groupId: String = "cornichon-groupId", sessionTimeoutMsConfig: Int = 10000, heartbeatIntervalMsConfig: Int = 100)

case class NotEnoughMessagesPolled(atLeastExpected: Int, messagesPolled: List[ConsumerRecord[String, String]]) extends CornichonError {
  lazy val baseErrorMessage: String = s"Not enough messages polled, expected at least $atLeastExpected but got ${messagesPolled.size}\n${messagesPolled.mkString("\n")}"
}