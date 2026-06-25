package no.nav.flaggskipet.infrastructure

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal fun dagensDato(
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.of("Europe/Oslo"),
) = now
    .toLocalDateTime(zone).date
