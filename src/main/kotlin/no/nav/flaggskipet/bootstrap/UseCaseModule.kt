package no.nav.flaggskipet.bootstrap

import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.resolve
import no.nav.flaggskipet.application.VurderTiltakspakkerUseCase

fun DependencyRegistry.useCaseModule() {
    provide<VurderTiltakspakkerUseCase> { VurderTiltakspakkerUseCase(resolve(), resolve()) }
}
