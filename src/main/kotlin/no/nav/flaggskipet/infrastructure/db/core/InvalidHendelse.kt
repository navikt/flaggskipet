package no.nav.flaggskipet.infrastructure.db.core

class InvalidHendelse(
    val topic: String,
    val partition: Int,
    val recordOffset: Long,
    val errorCode: String,
    val rawPayload: String?,
)
