package com.github.agourlay.cornichon.kafka

import com.github.agourlay.cornichon.dsl.Dsl
import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{ StringDeserializer, StringSerializer }
import com.github.agourlay.cornichon.kafka.KafkaDsl._
import org.apache.kafka.clients.consumer.{ ConsumerConfig, ConsumerRecord, KafkaConsumer }

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Future, Promise }

trait KafkaDsl {
  this: BaseFeature with Dsl ⇒

  def put_topic(topic: String, key: String, message: String) = EffectStep.fromAsync(
    title = s"put message $message with $key to  $topic",
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

  def read_from_topic(topic: String, amount: Int) = EffectStep.fromSync(
    title = s"reading the last $amount messages from $topic ",
    effect = s ⇒ {
      consumer.unsubscribe()
      consumer.subscribe(Seq(topic).asJava)
      val messages = ListBuffer.empty[ConsumerRecord[String, String]]
      var nothingNewAnymore = false
      while (!nothingNewAnymore) {
        val newMassages = consumer.poll(5000)
        val collectionOfNewMessages = newMassages.iterator().asScala.toList
        messages ++= collectionOfNewMessages
        nothingNewAnymore = newMassages.isEmpty
      }
      messages.drop(messages.size - amount)
      messages.foldLeft(s) { (session, value) ⇒
        session.addValue(topic, buildConsumerRecordJsonProjection(value)).valueUnsafe
      }
    }
  )
}

object KafkaDsl {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  lazy val kafkaConfig = ConfigFactory.load().as[KafkaConfig]("kafka")

  lazy val producer = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaConfig.bootstrapServers,
      ProducerConfig.ACKS_CONFIG -> kafkaConfig.producer.ack,
      ProducerConfig.BATCH_SIZE_CONFIG -> kafkaConfig.producer.batchSize.map(_.toString).getOrElse("")
    )

    val p = new KafkaProducer[String, String](configMap.asJava, new StringSerializer, new StringSerializer)
    BaseFeature.addShutdownHook(() ⇒ Future.successful(p.close()))
    p
  }

  lazy val consumer = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaConfig.bootstrapServers,
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "true",
      ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG -> "1000",
      ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG -> "300000",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.GROUP_ID_CONFIG -> "1234"
    )

    val c = new KafkaConsumer[String, String](configMap.asJava, new StringDeserializer, new StringDeserializer)
    BaseFeature.addShutdownHook(() ⇒ Future.successful(c.close()))
    c
  }

  def buildProducerRecord(topic: String, key: String, message: String): ProducerRecord[String, String] =
    new ProducerRecord[String, String](topic, key, message)

  def buildConsumerRecordJsonProjection(record: ConsumerRecord[String, String]) =
    s"""{
       |  "key": "${record.key()}",
       |  "topic": "${record.topic()}",
       |  "timestamp": "${record.timestamp()}",
       |  "value": "${record.value()}"
       |}""".stripMargin

}

case class KafkaConfig(
    bootstrapServers: String,
    producer: KafkaProducerConfig)

case class KafkaProducerConfig(ack: String = "all", batchSize: Option[Int], retriesConfig: Option[Int])
