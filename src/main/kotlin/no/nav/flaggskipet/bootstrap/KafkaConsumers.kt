package no.nav.flaggskipet.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import no.nav.flaggskipet.infrastructure.kafka.KafkaConsumerLifecycle
import org.koin.ktor.ext.inject
import java.time.Duration

internal fun Application.startKafkaConsumers() {
    val consumerLifecycles by inject<List<KafkaConsumerLifecycle<*, *>>>()

    consumerLifecycles.forEach { lifecycle ->
        lifecycle.start()
    }

    monitor.subscribe(ApplicationStopped) {
        consumerLifecycles.forEach { lifecycle ->
            lifecycle.stop(Duration.ofSeconds(5))
        }
    }
}
