package no.nav.flaggskipet.devtools.kafka

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Properties
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    VALID,
    INVALID,
    TOMBSTONE,
}

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun main(args: Array<String>) = runBlocking {
    val variant = args.firstOrNull()
        ?.uppercase()
        ?.let(Variant::valueOf)
        ?: Variant.VALID
    val bootstrapServers = envOrDefault(KafkaSykmeldingSettings.bootstrapServers)
    val topic = envOrDefault(KafkaSykmeldingSettings.topic)
    val eventId = UUID.randomUUID().toString()
    val key = "sykmelding-$eventId"

    KafkaProducer<String, String?>(producerProperties(bootstrapServers)).use { producer ->
        val value = when (variant) {
            Variant.VALID -> SykmeldingHendelseFixtures.validMessage()
            Variant.INVALID -> SykmeldingHendelseFixtures.mismatchedSykmeldingIdMessage()
            Variant.TOMBSTONE -> null
        }

        val metadata = producer.sendAndAwait(ProducerRecord(topic, key, value))
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

private suspend fun <K, V> KafkaProducer<K, V>.sendAndAwait(record: ProducerRecord<K, V>): RecordMetadata = suspendCancellableCoroutine { continuation ->
    val future = send(record) { metadata, exception ->
        when {
            exception != null -> continuation.resumeWithException(exception)

            metadata != null -> continuation.resume(metadata)

            else -> continuation.resumeWithException(
                IllegalStateException("Kafka send completed without metadata"),
            )
        }
    }

    continuation.invokeOnCancellation {
        future.cancel(true)
    }
}

private fun envOrDefault(setting: EnvSetting): String = System.getenv(setting.name)?.takeIf { it.isNotBlank() } ?: setting.defaultValue

private fun producerProperties(bootstrapServers: String): Properties = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    put(ProducerConfig.ACKS_CONFIG, "all")
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
}

internal object SykmeldingHendelseFixtures {
    fun validMessage(): String = """
    {
  "sykmelding": {
    "sykmeldingsperioder": [
      {
        "fom": "2026-06-10",
        "tom": "2026-06-20"
      },
      {
        "fom": "2026-06-21",
        "tom": "2026-06-30"
      }
    ],
    "syketilfelleStartDato": "2026-06-08"
  },
  "kafkaMetadata": {
    "sykmeldingId": "sm-123456789",
    "timestamp": "2026-06-22T10:15:30Z",
    "fnr": "12039456789",
    "source": "syk-system"
  },
  "event": {
    "sykmeldingId": "sm-123456789",
    "timestamp": "2026-06-22T10:15:30Z",
    "arbeidsgiver": {
      "orgnummer": "987654321",
      "juridiskOrgnummer": "123456789",
      "orgNavn": "Acme AS"
    },
    "brukerSvar": {
      "riktigNarmesteLeder": {
        "sporsmaltekst": "Er dette riktig nærmeste leder?",
        "svar": "ja"
      }
    }
  }
}
    """.trimIndent()

    fun mismatchedSykmeldingIdMessage(): String = """
        {
          "sykmelding": {
            "sykmeldingsperioder": [
              {
                "fom": "2026-06-10",
                "tom": "2026-06-20"
              },
              {
                "fom": "2026-06-21",
                "tom": "2026-06-30"
              }
            ],
            "syketilfelleStartDato": "2026-06-08"
          },
          "kafkaMetadata": {
            "sykmeldingId": "sm-123456789",
                        "fnr": "12039456789",
            "source": "syk-system"
          },
          "event": {
            "sykmeldingId": "sm-123456789",
            "arbeidsgiver": {
              "orgnummer": "987654321",
              "juridiskOrgnummer": "123456789",
              "orgNavn": "Acme AS"
            },
            "brukerSvar": {
              "riktigNarmesteLeder": {
                "sporsmaltekst": "Er dette riktig nærmeste leder?",
                "svar": "ja"
              }
            }
          }
        }
    """.trimIndent()
}
