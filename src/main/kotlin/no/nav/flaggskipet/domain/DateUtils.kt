package no.nav.flaggskipet.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal fun today(
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.of("Europe/Oslo"),
) = now
    .toLocalDateTime(zone).date
