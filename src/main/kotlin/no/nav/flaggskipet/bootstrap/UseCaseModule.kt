package no.nav.flaggskipet.bootstrap

import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.resolve
import no.nav.flaggskipet.application.AssessTiltakspakkerUseCase
import no.nav.flaggskipet.domain.assessment.activeTiltakspakker
import no.nav.flaggskipet.infrastructure.config.TiltakspakkeConfig

fun DependencyRegistry.useCaseModule() {
    provide<AssessTiltakspakkerUseCase> {
        AssessTiltakspakkerUseCase(
            eregClient = resolve(),
            tiltakspakkeAssessmentRepository = resolve(),
            tiltakspakker = activeTiltakspakker(
                tiltakspakke2Visible = resolve<TiltakspakkeConfig>().tiltakspakke2Visible,
            ),
        )
    }
}
