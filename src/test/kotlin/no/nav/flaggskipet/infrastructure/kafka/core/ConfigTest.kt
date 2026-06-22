package no.nav.flaggskipet.infrastructure.kafka.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig

class ConfigTest :
    FunSpec({
        test("fromConfig defaults consumer enabled to true") {
            val config = KafkaConfig.fromConfig(config())

            config.bootstrapServers shouldBe "localhost:9092"
            config.consumers.getValue("sykmelding").enabled shouldBe true
        }

        test("fromConfig reads consumer enabled override") {
            val config = KafkaConfig.fromConfig(config(enabled = "false"))

            config.consumers.getValue("sykmelding").enabled shouldBe false
        }

        test("fromConfig validates consumer enabled value") {
            shouldThrow<IllegalStateException> {
                KafkaConfig.fromConfig(config(enabled = "maybe"))
            }.message shouldBe "Invalid kafka configuration: kafka.consumers.sykmelding.enabled must be true or false"
        }
    })

private fun config(
    bootstrapServers: String = "localhost:9092",
    enabled: String = "",
    topic: String = "teamsykmelding.syfo-sendt-sykmelding",
    groupId: String = "flaggskipet-sykmelding-v1",
    autoOffsetReset: String = "earliest",
    maxPollRecords: String = "100",
    truststorePath: String = "",
    keystorePath: String = "",
    credentialStorePassword: String = "",
): MapApplicationConfig = MapApplicationConfig(
    "kafka.bootstrapServers" to bootstrapServers,
    "kafka.consumers.sykmelding.enabled" to enabled,
    "kafka.consumers.sykmelding.topic" to topic,
    "kafka.consumers.sykmelding.groupId" to groupId,
    "kafka.consumers.sykmelding.autoOffsetReset" to autoOffsetReset,
    "kafka.consumers.sykmelding.maxPollRecords" to maxPollRecords,
    "kafka.truststorePath" to truststorePath,
    "kafka.keystorePath" to keystorePath,
    "kafka.credentialStorePassword" to credentialStorePassword,
)
