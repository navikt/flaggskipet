package no.nav.flaggskipet.infrastructure.kafka.sykmelding

internal object SykmeldingKafkaMessageFixtures {
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
