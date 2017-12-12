package com.github.agourlay.cornichon.kafka

import com.github.agourlay.cornichon.feature.BaseFeature
import org.apache.kafka.clients.consumer.{ ConsumerConfig, KafkaConsumer }
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerConfig }
import org.apache.kafka.common.serialization.{ StringDeserializer, StringSerializer }

import scala.concurrent.Future
import scala.collection.JavaConverters._

trait KafkaConfig {

  def consumerGroup = s"cornichon-groupId"
  def bootstrapServers = "localhost:9092"

  protected lazy val producer = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> bootstrapServers,
      ProducerConfig.ACKS_CONFIG -> "all"
    )

    val p = new KafkaProducer[String, String](configMap.asJava, new StringSerializer, new StringSerializer)
    BaseFeature.addShutdownHook(() ⇒ Future {
      p.close()
    }(BaseFeature.globalScheduler))
    p
  }
  protected lazy val consumer = {
    val configMap = scala.collection.mutable.Map[String, AnyRef](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> bootstrapServers,
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false",
      ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG -> "100",
      ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG -> "10000",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.GROUP_ID_CONFIG -> consumerGroup
    )

    val c = new KafkaConsumer[String, String](configMap.asJava, new StringDeserializer, new StringDeserializer)
    BaseFeature.addShutdownHook(() ⇒ Future {
      c.close()
    }(BaseFeature.globalScheduler))
    c
  }
}
