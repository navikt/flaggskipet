package no.nav.flaggskipet.infrastructure.kafka.sykmelding

internal object SykmeldingKafkaMessageFixtures {
    fun validMessage(
        sykmeldingId: String,
        eventSykmeldingId: String = sykmeldingId,
        timestamp: String = "2026-01-15T10:15:30Z",
        periods: String = """
        [
          {
            "fom": "2026-01-01",
            "tom": "2026-01-05"
          },
          {
            "fom": "2026-01-06",
            "tom": "2026-01-10"
          }
        ]
        """.trimIndent(),
    ): String = """
    {
      "kafkaMetadata": {
        "sykmeldingId": "$sykmeldingId",
        "fnr": "00000000000",
        "timestamp": "$timestamp"
      },
      "event": {
        "sykmeldingId": "$eventSykmeldingId",
        "arbeidsgiver": {
          "orgnummer": "999888777"
        }
      },
      "sykmelding": {
        "sykmeldingsperioder": $periods
      }
    }
    """.trimIndent()

    fun mismatchedSykmeldingIdMessage(sykmeldingId: String): String = validMessage(
        sykmeldingId = sykmeldingId,
        eventSykmeldingId = "different-$sykmeldingId",
    )

    fun invalidPeriodMessage(sykmeldingId: String): String = validMessage(
        sykmeldingId = sykmeldingId,
        periods = """
        [
          {
            "fom": "2026-01-01"
          }
        ]
        """.trimIndent(),
    )
}
