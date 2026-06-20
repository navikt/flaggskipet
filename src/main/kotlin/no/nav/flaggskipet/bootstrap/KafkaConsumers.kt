package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import no.nav.flaggskipet.infrastructure.kafka.KafkaConsumerLifecycle
import org.koin.ktor.ext.inject
import java.time.Duration

internal fun Application.startKafkaConsumers() {
    val sykmeldingConsumerLifecycle by inject<KafkaConsumerLifecycle<String, ByteArray?>>()

    sykmeldingConsumerLifecycle.start()

    monitor.subscribe(ApplicationStopped) {
        sykmeldingConsumerLifecycle.stop(Duration.ofSeconds(5))
    }
}
