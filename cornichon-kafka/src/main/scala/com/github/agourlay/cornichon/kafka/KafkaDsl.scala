package com.github.agourlay.cornichon.kafka

import java.time.Duration

import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.dsl.CoreDsl
import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.steps.regular.EffectStep
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{ StringDeserializer, StringSerializer }
import com.github.agourlay.cornichon.kafka.KafkaDsl._
import org.apache.kafka.clients.consumer.{ ConsumerConfig, ConsumerRecord, KafkaConsumer }
import pureconfig.{ CamelCase, ConfigFieldMapping, ProductHint }

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Future, Promise }

trait KafkaDsl {
  this: BaseFeature with CoreDsl ⇒

  // Kafka tests can not run in //
  override lazy val executeScenariosInParallel: Boolean = false

  def put_topic(topic: String, key: String, message: String) = EffectStep.fromAsync(
    title = s"put message=$message with key=$key to topic=$topic",
    effect = s ⇒ {
      val pr = buildProducerRecord(topic, key, message)
      val p = Promise[Unit]()
      producer.send(pr, new Callback {
        def onCompletion(metadata: RecordMetadata, exception: Exception): Unit =
          if (exception == null)
            p.success(())
          else
            p.failure(exception)
      })
      p.future.map(_ ⇒ s)
    }
  )

  def read_from_topic(topic: String, amount: Int = 1, timeout: Int = 500) = EffectStep.fromAsync(
    title = s"reading the last $amount messages from topic=$topic",
    effect = s ⇒ Future {
      consumer.unsubscribe()
      consumer.subscribe(Seq(topic).asJava)
      val messages = ListBuffer.empty[ConsumerRecord[String, String]]
      var nothingNewAnymore = false
      val pollDuration = Duration.ofMillis(timeout.toLong)
      while (!nothingNewAnymore) {
        val newMessages = consumer.poll(pollDuration)
        val collectionOfNewMessages = newMessages.iterator().asScala.toList
        messages ++= collectionOfNewMessages
        nothingNewAnymore = newMessages.isEmpty
      }
      consumer.commitSync()
      messages.drop(messages.size - amount)
      messages.foldLeft(s) { (session, value) ⇒
        commonSessionExtraction(session, topic, value).valueUnsafe
      }
    }
  )

  def kafka(topic: String) = KafkaStepBuilder(
    sessionKey = topic,
    placeholderResolver = placeholderResolver,
    matcherResolver = matcherResolver
  )

  private def commonSessionExtraction(session: Session, topic: String, response: ConsumerRecord[String, String]) =
    session.addValues(
      s"$topic-topic" → response.topic(),
      s"$topic-key" → response.key(),
      s"$topic-value" → response.value()
    )

}

object KafkaDsl {
  import scala.concurrent.ExecutionContext.Implicits.global

  // if the config does not exist we use the default values
  lazy val kafkaConfig = pureconfig.loadConfigOrThrow[Option[KafkaConfig]]("kafka").getOrElse(KafkaConfig())

  lazy val producer = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaConfig.bootstrapServers,
      ProducerConfig.ACKS_CONFIG -> kafkaConfig.producer.ack,
      ProducerConfig.BATCH_SIZE_CONFIG -> kafkaConfig.producer.batchSizeInBytes.toString
    )

    val p = new KafkaProducer[String, String](configMap.asJava, new StringSerializer, new StringSerializer)
    BaseFeature.addShutdownHook(() ⇒ Future {
      p.close()
    })
    p
  }

  lazy val consumer = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaConfig.bootstrapServers,
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false",
      ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG -> "100",
      ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG -> "10000",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.GROUP_ID_CONFIG -> kafkaConfig.consumer.groupId
    )

    val c = new KafkaConsumer[String, String](configMap.asJava, new StringDeserializer, new StringDeserializer)
    BaseFeature.addShutdownHook(() ⇒ Future {
      c.close()
    })
    c
  }

  def buildProducerRecord(topic: String, key: String, message: String): ProducerRecord[String, String] =
    new ProducerRecord[String, String](topic, key, message)

  def buildConsumerRecordJsonProjection(f: String ⇒ String)(record: ConsumerRecord[String, String]): String =
    s"""{
       |  "key": "${record.key()}",
       |  "topic": "${record.topic()}",
       |  "timestamp": "${record.timestamp()}",
       |  "value": ${f(record.value())}
       |}""".stripMargin

}

case class KafkaConfig(
    bootstrapServers: String = "localhost:9092",
    producer: KafkaProducerConfig = KafkaProducerConfig(),
    consumer: KafkaConsumerConfig = KafkaConsumerConfig())

object KafkaConfig {
  implicit val hint = ProductHint[KafkaConfig](allowUnknownKeys = false, fieldMapping = ConfigFieldMapping(CamelCase, CamelCase))
}

case class KafkaProducerConfig(ack: String = "all", batchSizeInBytes: Int = 1, retriesConfig: Option[Int] = None)

case class KafkaConsumerConfig(groupId: String = s"cornichon-groupId")
