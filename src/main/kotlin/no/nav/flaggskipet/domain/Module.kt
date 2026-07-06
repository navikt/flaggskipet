package no.nav.flaggskipet.domain

import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.provide
import io.ktor.server.plugins.di.resolve
import no.nav.flaggskipet.domain.vurdering.VurderTiltakspakkerUseCase

fun DependencyRegistry.useCaseModule() {
    provide<VurderTiltakspakkerUseCase> { VurderTiltakspakkerUseCase(resolve(), resolve()) }
}
