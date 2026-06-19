package no.nav.flaggskipet.infrastructure.kafka

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaPropertiesFactoryTest :
    FunSpec({
        test("consumer uses plaintext locally and disables auto commit") {
            val properties = KafkaPropertiesFactory(
                KafkaConfig(
                    bootstrapServers = "localhost:9092",
                    consumers = mapOf(
                        "sykmelding" to KafkaConsumerConfig(
                            topic = "teamsykmelding.syfo-sendt-sykmelding",
                            groupId = "flaggskipet-sykmelding-v1",
                            autoOffsetReset = "earliest",
                        ),
                    ),
                    security = KafkaSecurityConfig.Plaintext,
                ),
            ).consumer(
                groupId = "flaggskipet-sykmelding-v1",
                autoOffsetReset = "earliest",
                maxPollRecords = 100,
                keyDeserializer = StringDeserializer::class.java,
                valueDeserializer = StringDeserializer::class.java,
            )

            properties.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) shouldBe "localhost:9092"
            properties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG) shouldBe "PLAINTEXT"
            properties.getProperty(ConsumerConfig.GROUP_ID_CONFIG) shouldBe "flaggskipet-sykmelding-v1"
            properties.getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG) shouldBe "earliest"
            properties.getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG) shouldBe "100"
            properties.getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG) shouldBe "false"
            properties.getProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG) shouldBe StringDeserializer::class.java.name
            properties.getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG) shouldBe StringDeserializer::class.java.name
        }

        test("consumer uses ssl when nais kafka credentials are configured") {
            val properties = KafkaPropertiesFactory(
                KafkaConfig(
                    bootstrapServers = "broker-a:9093,broker-b:9093",
                    consumers = mapOf(
                        "sykmelding" to KafkaConsumerConfig(
                            topic = "teamsykmelding.syfo-sendt-sykmelding",
                            groupId = "flaggskipet-sykmelding-v1",
                            autoOffsetReset = "latest",
                        ),
                    ),
                    security = KafkaSecurityConfig.Ssl(
                        truststorePath = "/var/run/secrets/truststore.p12",
                        keystorePath = "/var/run/secrets/keystore.p12",
                        credentialStorePassword = "supersecret",
                    ),
                ),
            ).consumer(
                groupId = "flaggskipet-sykmelding-v1",
                autoOffsetReset = "latest",
                maxPollRecords = 250,
                keyDeserializer = StringDeserializer::class.java,
                valueDeserializer = StringDeserializer::class.java,
            )

            properties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG) shouldBe "SSL"
            properties.getProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG) shouldBe "PKCS12"
            properties.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG) shouldBe "/var/run/secrets/truststore.p12"
            properties.getProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG) shouldBe "PKCS12"
            properties.getProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG) shouldBe "/var/run/secrets/keystore.p12"
            properties.getProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG) shouldBe "supersecret"
            properties.getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG) shouldBe "supersecret"
            properties.getProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG) shouldBe "supersecret"
        }
    })
