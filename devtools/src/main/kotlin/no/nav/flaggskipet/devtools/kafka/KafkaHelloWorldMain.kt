package no.nav.flaggskipet.devtools.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.time.Instant
import java.util.Properties

private data class EnvSetting(
    val name: String,
    val defaultValue: String,
)

private object KafkaHelloWorldSettings {
    val bootstrapServers = EnvSetting(
        name = "FLAGGSKIPET_KAFKA_BOOTSTRAP_SERVERS",
        defaultValue = "localhost:9092",
    )
    val topic = EnvSetting(
        name = "FLAGGSKIPET_KAFKA_HELLO_WORLD_TOPIC",
        defaultValue = "flaggskipet.dev.hello-world",
    )
}

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun main() {
    val bootstrapServers = envOrDefault(KafkaHelloWorldSettings.bootstrapServers)
    val topic = envOrDefault(KafkaHelloWorldSettings.topic)
    val key = "hello-world"
    val value = """{"message":"hello world","createdAt":"${Instant.now()}"}"""

    val producer = KafkaProducer<String, String>(producerProperties(bootstrapServers))
    try {
        val metadata = producer.send(ProducerRecord<String, String>(topic, key, value)).get()
        logger.info(
            "Sent message to topic=${metadata.topic()}, partition=${metadata.partition()}, offset=${metadata.offset()}, key=$key, value=$value",
        )
    } finally {
        producer.close()
    }
}

private fun envOrDefault(setting: EnvSetting): String =
    System.getenv(setting.name)?.takeIf { it.isNotBlank() } ?: setting.defaultValue

private fun producerProperties(bootstrapServers: String): Properties = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    put(ProducerConfig.ACKS_CONFIG, "all")
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
}
