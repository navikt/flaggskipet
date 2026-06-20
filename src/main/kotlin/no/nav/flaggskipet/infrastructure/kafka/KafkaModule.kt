package no.nav.flaggskipet.infrastructure.kafka

import no.nav.flaggskipet.bootstrap.ApplicationState
import no.nav.flaggskipet.infrastructure.kafka.sykmelding.NullableByteArrayDeserializer
import no.nav.flaggskipet.infrastructure.kafka.sykmelding.SykmeldingKafkaMessageDecoder
import no.nav.flaggskipet.infrastructure.kafka.sykmelding.SykmeldingKafkaMessageHandler
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.koin.core.module.Module
import org.koin.dsl.module

fun kafkaModule(kafkaConfig: KafkaConfig): Module {
    val consumerConfig = kafkaConfig.consumers["sykmelding"]
        ?: error("Kafka consumer configuration 'sykmelding' is missing")
    val propertiesFactory = KafkaPropertiesFactory(kafkaConfig)

    return module {
        single { propertiesFactory }
        single { SykmeldingKafkaMessageDecoder() }
        single { SykmeldingKafkaMessageHandler(get(), get(), get()) }
        single<KafkaConsumerRunner<String, ByteArray?>> {
            KafkaConsumerRunner(
                consumer = KafkaConsumer<String, ByteArray?>(
                    propertiesFactory.consumer(
                        groupId = consumerConfig.groupId,
                        autoOffsetReset = consumerConfig.autoOffsetReset,
                        maxPollRecords = consumerConfig.maxPollRecords,
                        keyDeserializer = StringDeserializer::class.java,
                        valueDeserializer = NullableByteArrayDeserializer::class.java,
                    ),
                ),
                topics = listOf(consumerConfig.topic),
                handler = get<SykmeldingKafkaMessageHandler>(),
            )
        }
        single<KafkaConsumerLifecycle<String, ByteArray?>> {
            KafkaConsumerLifecycle(
                consumerName = "sykmelding",
                runner = get<KafkaConsumerRunner<String, ByteArray?>>(),
                applicationState = get<ApplicationState>(),
            )
        }
    }
}
