package no.nav.flaggskipet.domain

import no.nav.flaggskipet.domain.vurdering.VurderTiltakspakkerUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

fun useCaseModule(): Module = module {
    single<VurderTiltakspakkerUseCase> { VurderTiltakspakkerUseCase(get(), get()) }
}
