package no.nav.flaggskipet.infrastructure.kafka.core

import no.nav.flaggskipet.infrastructure.kafka.sykmelding.SykmeldingHendelseHandler
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.koin.core.module.Module
import org.koin.dsl.module

fun kafkaModule(): Module = module {
    single<MessageHandler<String, String?>> { SykmeldingHendelseHandler(get()) }
    single<ConsumerErrorHandler<String, String?>> { InvalidHendelseHandler(get()) }
    single<List<ConsumerRunner<*, *>>> {
        val kafkaConfig = get<KafkaConfig>()
        val enabledConsumers = kafkaConfig.consumers.filterValues { it.enabled }
        enabledConsumers.map { (name, consumerConfig) ->
            ConsumerRunner<String, String?>(
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
                handler = get<MessageHandler<String, String?>>(),
                errorHandler = get<ConsumerErrorHandler<String, String?>>(),
                coroutineName = "$name-kafka-consumer",
            )
        }
    }
}
