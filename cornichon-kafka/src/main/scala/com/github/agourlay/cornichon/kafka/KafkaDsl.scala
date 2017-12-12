package com.github.agourlay.cornichon.kafka

import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.dsl.Dsl
import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{ StringDeserializer, StringSerializer }
import com.github.agourlay.cornichon.kafka.KafkaDsl._
import org.apache.kafka.clients.consumer.{ ConsumerConfig, ConsumerRecord, KafkaConsumer }
import org.apache.kafka.common.TopicPartition

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Future, Promise }

trait KafkaDsl {
  this: BaseFeature with Dsl ⇒

  override private[cornichon] lazy val config = BaseFeature.config.copy(executeScenariosInParallel = false)

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

  def read_from_topic(topic: String, amount: Int, targetKey: Option[String] = None, timeout: Int = 500) = EffectStep.fromAsync(
    title = s"reading the last $amount messages from topic=$topic",
    effect = s ⇒ readFromTopic(topic, targetKey.getOrElse(topic), amount, timeout, s)(buildConsumerRecordJsonProjection(v ⇒ s""" "$v" """))
  )

  def read_json_from_topic(topic: String, amount: Int, targetKey: Option[String] = None, timeout: Int = 500) = EffectStep.fromAsync(
    title = s"reading the last $amount messages from topic=$topic ",
    effect = s ⇒ readFromTopic(topic, targetKey.getOrElse(topic), amount, timeout, s)(buildConsumerRecordJsonProjection(v ⇒ CornichonJson.jsonStringValue(CornichonJson.parseJson(v).valueUnsafe)))
  )

  private def readFromTopic(topic: String, targetKey: String, amount: Int, timeout: Int, s: Session)(transformRecord: ConsumerRecord[String, String] ⇒ String) = Future {
    consumer.unsubscribe()
    val partitions = consumer.partitionsFor(topic).asScala.map { partitionInfo ⇒
      new TopicPartition(
        partitionInfo.topic(),
        partitionInfo.partition()
      )
    }.asJava
    consumer.assign(partitions)
    val messages = ListBuffer.empty[ConsumerRecord[String, String]]
    var nothingNewAnymore = false
    while (!nothingNewAnymore) {
      val newMessages = consumer.poll(timeout)
      val collectionOfNewMessages = newMessages.iterator().asScala.toList
      messages ++= collectionOfNewMessages
      nothingNewAnymore = newMessages.isEmpty
    }
    consumer.commitSync()
    messages.drop(messages.size - amount)
    messages.foldLeft(s) { (session, value) ⇒
      session.addValue(targetKey, transformRecord(value)).valueUnsafe
    }
  }
}

object KafkaDsl {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  lazy val kafkaConfig = ConfigFactory.load().as[KafkaConfig]("kafka")

  lazy val producer = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaConfig.bootstrapServers,
      ProducerConfig.ACKS_CONFIG -> kafkaConfig.producer.ack,
      ProducerConfig.BATCH_SIZE_CONFIG -> kafkaConfig.producer.batchSizeInBytes.toString
    )

    val p = new KafkaProducer[String, String](configMap.asJava, new StringSerializer, new StringSerializer)
    BaseFeature.addShutdownHook(() ⇒ Future {
      p.close()
    }(BaseFeature.globalScheduler))
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
    }(BaseFeature.globalScheduler))
    c
  }

  def buildProducerRecord(topic: String, key: String, message: String): ProducerRecord[String, String] =
    new ProducerRecord[String, String](topic, key, message)

  def buildConsumerRecordJsonProjection(f: String ⇒ String)(record: ConsumerRecord[String, String]) =
    s"""{
       |  "key": "${record.key()}",
       |  "topic": "${record.topic()}",
       |  "timestamp": "${record.timestamp()}",
       |  "value": ${f(record.value())}
       |}""".stripMargin

}

case class KafkaConfig(
    bootstrapServers: String,
    producer: KafkaProducerConfig,
    consumer: KafkaConsumerConfig)

case class KafkaProducerConfig(ack: String = "all", batchSizeInBytes: Int = 1, retriesConfig: Option[Int])

case class KafkaConsumerConfig(groupId: String = s"cornichon-groupId")
