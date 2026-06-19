package no.nav.flaggskipet.infrastructure.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.config.MapApplicationConfig

class KafkaConfigTest :
    FunSpec({
        test("fromConfig reads kafka properties") {
            with(KafkaConfig.fromConfig(config())) {
                bootstrapServers shouldBe "localhost:9092"
                consumers["sykmelding"] shouldBe KafkaConsumerConfig(
                    topic = "teamsykmelding.syfo-sendt-sykmelding",
                    groupId = "flaggskipet-sykmelding-v1",
                    autoOffsetReset = "earliest",
                )
                security shouldBe KafkaSecurityConfig.Plaintext
            }
        }

        test("fromConfig reads multiple named consumers") {
            with(
                KafkaConfig.fromConfig(
                    config(
                        extraValues = mapOf(
                            "kafka.consumers.narmesteleder.topic" to "teamsykmelding.syfo-narmesteleder-leesah",
                            "kafka.consumers.narmesteleder.groupId" to "flaggskipet-narmesteleder-v1",
                            "kafka.consumers.narmesteleder.autoOffsetReset" to "latest",
                        ),
                    ),
                ),
            ) {
                consumers.keys shouldBe setOf("narmesteleder", "sykmelding")
                consumers["narmesteleder"] shouldBe KafkaConsumerConfig(
                    topic = "teamsykmelding.syfo-narmesteleder-leesah",
                    groupId = "flaggskipet-narmesteleder-v1",
                    autoOffsetReset = "latest",
                )
            }
        }

        test("fromConfig reads optional ssl properties") {
            with(
                KafkaConfig.fromConfig(
                    config(
                        truststorePath = "/var/run/secrets/truststore.p12",
                        keystorePath = "/var/run/secrets/keystore.p12",
                        credentialStorePassword = "supersecret",
                    ),
                ),
            ) {
                security shouldBe KafkaSecurityConfig.Ssl(
                    truststorePath = "/var/run/secrets/truststore.p12",
                    keystorePath = "/var/run/secrets/keystore.p12",
                    credentialStorePassword = "supersecret",
                )
            }
        }

        test("fromConfig reports all missing required kafka properties") {
            with(
                shouldThrow<IllegalStateException> {
                    KafkaConfig.fromConfig(
                        config(
                            bootstrapServers = "",
                            topic = "",
                            groupId = "",
                            autoOffsetReset = "",
                        ),
                    )
                },
            ) {
                message shouldBe
                    "Invalid kafka configuration: " +
                    "kafka.bootstrapServers must be set, " +
                    "kafka.consumers.sykmelding.topic must be set, " +
                    "kafka.consumers.sykmelding.groupId must be set, " +
                    "kafka.consumers.sykmelding.autoOffsetReset must be set"
            }
        }

        test("fromConfig requires at least one consumer") {
            with(
                shouldThrow<IllegalStateException> {
                    KafkaConfig.fromConfig(
                        MapApplicationConfig(
                            "kafka.bootstrapServers" to "localhost:9092",
                            "kafka.truststorePath" to "",
                            "kafka.keystorePath" to "",
                            "kafka.credentialStorePassword" to "",
                        ),
                    )
                },
            ) {
                message shouldBe "Invalid kafka configuration: kafka.consumers must define at least one consumer"
            }
        }

        test("fromConfig validates auto offset reset") {
            with(
                shouldThrow<IllegalStateException> {
                    KafkaConfig.fromConfig(config(autoOffsetReset = "middle"))
                },
            ) {
                message shouldBe
                    "Invalid kafka configuration: " +
                    "kafka.consumers.sykmelding.autoOffsetReset must be one of earliest, latest, none"
            }
        }

        test("fromConfig defaults maxPollRecords when not set") {
            KafkaConfig.fromConfig(config()).consumers["sykmelding"]?.maxPollRecords shouldBe
                KafkaConsumerConfig.DEFAULT_MAX_POLL_RECORDS
        }

        test("fromConfig reads maxPollRecords override") {
            KafkaConfig.fromConfig(config(maxPollRecords = "250")).consumers["sykmelding"]?.maxPollRecords shouldBe 250
        }

        test("fromConfig validates maxPollRecords is a positive integer") {
            with(
                shouldThrow<IllegalStateException> {
                    KafkaConfig.fromConfig(config(maxPollRecords = "0"))
                },
            ) {
                message shouldBe
                    "Invalid kafka configuration: " +
                    "kafka.consumers.sykmelding.maxPollRecords must be a positive integer"
            }
        }

        test("fromConfig validates partial ssl configuration") {
            with(
                shouldThrow<IllegalStateException> {
                    KafkaConfig.fromConfig(config(truststorePath = "/var/run/secrets/truststore.p12"))
                },
            ) {
                message shouldBe
                    "Invalid kafka configuration: " +
                    "kafka.truststorePath, kafka.keystorePath and " +
                    "kafka.credentialStorePassword must either all be set or all be blank"
            }
        }

        test("toString masks password") {
            with(
                KafkaConfig(
                    bootstrapServers = "localhost:9092",
                    consumers = mapOf(
                        "sykmelding" to KafkaConsumerConfig(
                            topic = "teamsykmelding.syfo-sendt-sykmelding",
                            groupId = "flaggskipet-sykmelding-v1",
                            autoOffsetReset = "earliest",
                        ),
                    ),
                    security = KafkaSecurityConfig.Ssl(
                        truststorePath = "/var/run/secrets/truststore.p12",
                        keystorePath = "/var/run/secrets/keystore.p12",
                        credentialStorePassword = "supersecret",
                    ),
                ),
            ) {
                toString().shouldContain("credentialStorePassword=***")
                toString().shouldNotContain("supersecret")
            }
        }

        test("security config toString masks password") {
            KafkaSecurityConfig.Ssl(
                truststorePath = "/var/run/secrets/truststore.p12",
                keystorePath = "/var/run/secrets/keystore.p12",
                credentialStorePassword = "supersecret",
            ).toString().shouldNotContain("supersecret")
        }
    })

private fun config(
    bootstrapServers: String = "localhost:9092",
    topic: String = "teamsykmelding.syfo-sendt-sykmelding",
    groupId: String = "flaggskipet-sykmelding-v1",
    autoOffsetReset: String = "earliest",
    maxPollRecords: String = "",
    truststorePath: String = "",
    keystorePath: String = "",
    credentialStorePassword: String = "",
    extraValues: Map<String, String> = emptyMap(),
): MapApplicationConfig = MapApplicationConfig(
    "kafka.bootstrapServers" to bootstrapServers,
    "kafka.consumers.sykmelding.topic" to topic,
    "kafka.consumers.sykmelding.groupId" to groupId,
    "kafka.consumers.sykmelding.autoOffsetReset" to autoOffsetReset,
    "kafka.consumers.sykmelding.maxPollRecords" to maxPollRecords,
    "kafka.truststorePath" to truststorePath,
    "kafka.keystorePath" to keystorePath,
    "kafka.credentialStorePassword" to credentialStorePassword,
    *extraValues.map { (key, value) -> key to value }.toTypedArray(),
)
