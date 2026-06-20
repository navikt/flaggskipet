package no.nav.flaggskipet.infrastructure.kafka

enum class KafkaConsumerName(
    val configKey: String,
) {
    SYKMELDING("sykmelding"),
}
