package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import org.apache.kafka.common.serialization.Deserializer

class NullableByteArrayDeserializer : Deserializer<ByteArray?> {
    override fun deserialize(
        topic: String?,
        data: ByteArray?,
    ): ByteArray? = data?.takeUnless { it.isEmpty() }
}
