package no.nav.flaggskipet.infrastructure

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val osloTimeZone = TimeZone.of("Europe/Oslo")

internal fun dagensDato(now: Instant = Clock.System.now()): String = now
    .toLocalDateTime(osloTimeZone)
    .date
    .toString()
