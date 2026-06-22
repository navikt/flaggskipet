package no.nav.flaggskipet.infrastructure.kafka.core

import no.nav.flaggskipet.infrastructure.kafka.sykmelding.SykmeldingHendelseHandler
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun kafkaModule(kafkaConfig: KafkaConfig): Module = module {
    single<MessageHandler<String, String?>> { SykmeldingHendelseHandler(get()) }
    single<ConsumerErrorHandler<String, String?>> { InvalidHendelseHandler(get()) }

// Register each consumer as a named singleton (one per configured consumer).
    // Each gets its own KafkaConsumer instance, topic subscription, etc.
    kafkaConfig.consumers.forEach { (name, consumerConfig) ->
        val qualifier = named(name)

        single<ConsumerRunner<String, String?>>(qualifier) {
            ConsumerRunner(
                consumerFactory = {
                    KafkaConsumer(
                        PropertiesFactory(kafkaConfig).consumer(
                            groupId = consumerConfig.groupId,
                            autoOffsetReset = consumerConfig.autoOffsetReset,
                            maxPollRecords = consumerConfig.maxPollRecords,
                            keyDeserializer = StringDeserializer::class.java,
                            valueDeserializer = StringDeserializer::class.java,
                        ),
                    )
                },
                topics = listOf(consumerConfig.topic),
                handler = get(),
                onError = get(),
                coroutineName = "$name-kafka-consumer",
            )
        }
    }
    // Aggregate all named runners into a single injectable list for bulk lifecycle
    // management (start/close all at once). Does NOT create duplicate instances —
    // it resolves the same named singletons registered above.
    single<List<ConsumerRunner<*, *>>> {
        kafkaConfig.consumers.keys.map { name ->
            get(named(name))
        }
    }
}
