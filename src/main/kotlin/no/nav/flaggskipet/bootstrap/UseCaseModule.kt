package no.nav.flaggskipet.bootstrap

import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.resolve
import no.nav.flaggskipet.application.AssessTiltakspakkerUseCase

fun DependencyRegistry.useCaseModule() {
    provide<AssessTiltakspakkerUseCase> { AssessTiltakspakkerUseCase(resolve(), resolve()) }
}
