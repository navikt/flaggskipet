package no.nav.flaggskipet.devtools.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.time.Instant
import java.util.Properties
import java.util.UUID

private data class EnvSetting(
    val name: String,
    val defaultValue: String,
)

private object KafkaSykmeldingSettings {
    val bootstrapServers = EnvSetting(
        name = "FLAGGSKIPET_KAFKA_BOOTSTRAP_SERVERS",
        defaultValue = "localhost:9092",
    )
    val topic = EnvSetting(
        name = "FLAGGSKIPET_KAFKA_SYKMELDING_TOPIC",
        defaultValue = "teamsykmelding.syfo-sendt-sykmelding",
    )
}

private enum class Variant {
    valid,
    invalid,
    tombstone,
}

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun main(args: Array<String>) {
    val variant = args.firstOrNull()?.let(Variant::valueOf) ?: Variant.valid
    val bootstrapServers = envOrDefault(KafkaSykmeldingSettings.bootstrapServers)
    val topic = envOrDefault(KafkaSykmeldingSettings.topic)
    val eventId = UUID.randomUUID().toString()
    val key = "sykmelding-$eventId"

    KafkaProducer<String, String?>(producerProperties(bootstrapServers)).use { producer ->
        val value = when (variant) {
            Variant.valid -> validMessage(eventId)
            Variant.invalid -> invalidMessage(eventId)
            Variant.tombstone -> null
        }

        val metadata = producer.send(ProducerRecord(topic, key, value)).get()
        logger.info(
            "Sent sykmelding {} message to topic={}, partition={}, offset={}, key={}",
            variant,
            metadata.topic(),
            metadata.partition(),
            metadata.offset(),
            key,
        )
    }
}

private fun validMessage(eventId: String): String = """
{
  "kafkaMetadata": {
    "sykmeldingId": "$eventId",
    "fnr": "00000000000",
    "timestamp": "${Instant.now()}"
  },
  "event": {
    "sykmeldingId": "$eventId",
    "arbeidsgiver": {
      "orgnummer": "999888777"
    }
  },
  "sykmelding": {
    "sykmeldingsperioder": [
      {
        "fom": "2026-01-01",
        "tom": "2026-01-05"
      },
      {
        "fom": "2026-01-06",
        "tom": "2026-01-10"
      }
    ]
  }
}
""".trimIndent()

private fun invalidMessage(eventId: String): String = """
{
  "kafkaMetadata": {
    "sykmeldingId": "$eventId",
    "fnr": "00000000000",
    "timestamp": "${Instant.now()}"
  },
  "event": {
    "sykmeldingId": "different-$eventId"
  },
  "sykmelding": {
    "sykmeldingsperioder": [
      {
        "fom": "2026-01-01"
      }
    ]
  }
}
""".trimIndent()

private fun envOrDefault(setting: EnvSetting): String =
    System.getenv(setting.name)?.takeIf { it.isNotBlank() } ?: setting.defaultValue

private fun producerProperties(bootstrapServers: String): Properties = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    put(ProducerConfig.ACKS_CONFIG, "all")
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
}
