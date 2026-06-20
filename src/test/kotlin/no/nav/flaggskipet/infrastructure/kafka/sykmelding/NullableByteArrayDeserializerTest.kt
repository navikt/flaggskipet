package no.nav.flaggskipet.infrastructure.kafka.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class NullableByteArrayDeserializerTest :
    FunSpec({
        val deserializer = NullableByteArrayDeserializer()

        test("deserializer returns null for null payload") {
            deserializer.deserialize("topic", null).shouldBeNull()
        }

        test("deserializer returns null for empty payload") {
            deserializer.deserialize("topic", ByteArray(0)).shouldBeNull()
        }

        test("deserializer returns bytes for non-empty payload") {
            val payload = "hello".encodeToByteArray()

            deserializer.deserialize("topic", payload) shouldBe payload
        }
    })
