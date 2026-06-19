package no.nav.flaggskipet.infrastructure.kafka

import io.ktor.server.config.ApplicationConfig
import no.nav.flaggskipet.infrastructure.config.stringOrEmpty

data class KafkaConsumerConfig(
    val topic: String,
    val groupId: String,
    val autoOffsetReset: String,
    val maxPollRecords: Int = DEFAULT_MAX_POLL_RECORDS,
) {
    companion object {
        const val DEFAULT_MAX_POLL_RECORDS = 100
    }
}

data class KafkaConfig(
    val bootstrapServers: String,
    val consumers: Map<String, KafkaConsumerConfig>,
    val security: KafkaSecurityConfig,
) {
    companion object {
        private val supportedAutoOffsetResets = setOf("earliest", "latest", "none")
        private val consumerPathPattern =
            Regex("""kafka\.consumers\.([^.]+)\.(topic|groupId|autoOffsetReset|maxPollRecords)""")

        fun fromConfig(config: ApplicationConfig): KafkaConfig {
            fun value(key: String): String = config.stringOrEmpty("kafka.$key")

            val bootstrapServers = value("bootstrapServers")
            val consumerNames = config.consumerNames()
            val consumers = consumerNames.associateWith { consumerName ->
                val maxPollRecords = value("consumers.$consumerName.maxPollRecords").trim()
                KafkaConsumerConfig(
                    topic = value("consumers.$consumerName.topic"),
                    groupId = value("consumers.$consumerName.groupId"),
                    autoOffsetReset = value("consumers.$consumerName.autoOffsetReset").trim().lowercase(),
                    maxPollRecords = maxPollRecords.toIntOrNull()?.takeIf { it > 0 }
                        ?: KafkaConsumerConfig.DEFAULT_MAX_POLL_RECORDS,
                )
            }
            val truststorePath = value("truststorePath")
            val keystorePath = value("keystorePath")
            val credentialStorePassword = value("credentialStorePassword")

            val errors = buildList {
                if (bootstrapServers.isBlank()) add("kafka.bootstrapServers must be set")
                if (consumerNames.isEmpty()) add("kafka.consumers must define at least one consumer")
                consumers.forEach { (consumerName, consumer) ->
                    val path = "kafka.consumers.$consumerName"
                    if (consumer.topic.isBlank()) add("$path.topic must be set")
                    if (consumer.groupId.isBlank()) add("$path.groupId must be set")
                    when {
                        consumer.autoOffsetReset.isBlank() -> add("$path.autoOffsetReset must be set")
                        consumer.autoOffsetReset !in supportedAutoOffsetResets ->
                            add("$path.autoOffsetReset must be one of ${supportedAutoOffsetResets.joinToString(", ")}")
                    }
                    val maxPollRecords = value("consumers.$consumerName.maxPollRecords").trim()
                    if (maxPollRecords.isNotBlank()) {
                        val parsed = maxPollRecords.toIntOrNull()
                        if (parsed == null || parsed <= 0) {
                            add("$path.maxPollRecords must be a positive integer")
                        }
                    }
                }

                val sslValues = listOf(
                    "kafka.truststorePath" to truststorePath,
                    "kafka.keystorePath" to keystorePath,
                    "kafka.credentialStorePassword" to credentialStorePassword,
                )
                val configuredSslValues = sslValues.filter { (_, value) -> value.isNotBlank() }
                if (configuredSslValues.isNotEmpty() && configuredSslValues.size != sslValues.size) {
                    add(
                        "kafka.truststorePath, kafka.keystorePath and " +
                            "kafka.credentialStorePassword must either all be set or all be blank",
                    )
                }
            }

            check(errors.isEmpty()) {
                "Invalid kafka configuration: ${errors.joinToString(", ")}"
            }

            return KafkaConfig(
                bootstrapServers = bootstrapServers,
                consumers = consumers,
                security = KafkaSecurityConfig.from(
                    truststorePath = truststorePath,
                    keystorePath = keystorePath,
                    credentialStorePassword = credentialStorePassword,
                ),
            )
        }

        private fun ApplicationConfig.consumerNames(): Set<String> = keys()
            .mapNotNull { key -> consumerPathPattern.matchEntire(key)?.groupValues?.get(1) }
            .toSortedSet()
    }
}

sealed interface KafkaSecurityConfig {
    data object Plaintext : KafkaSecurityConfig

    data class Ssl(
        val truststorePath: String,
        val keystorePath: String,
        private val credentialStorePassword: String,
    ) : KafkaSecurityConfig {
        fun password(): String = credentialStorePassword

        override fun toString(): String =
            "Ssl(" +
                "truststorePath=$truststorePath, " +
                "keystorePath=$keystorePath, " +
                "credentialStorePassword=***)"
    }

    companion object {
        fun from(
            truststorePath: String,
            keystorePath: String,
            credentialStorePassword: String,
        ): KafkaSecurityConfig =
            if (truststorePath.isBlank() && keystorePath.isBlank() && credentialStorePassword.isBlank()) {
                Plaintext
            } else {
                Ssl(
                    truststorePath = truststorePath,
                    keystorePath = keystorePath,
                    credentialStorePassword = credentialStorePassword,
                )
            }
    }
}
