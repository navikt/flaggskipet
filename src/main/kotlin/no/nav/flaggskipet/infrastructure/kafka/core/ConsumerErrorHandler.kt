package no.nav.flaggskipet.infrastructure.kafka.core

import org.apache.kafka.clients.consumer.ConsumerRecord

fun interface ConsumerErrorHandler<K, V> {
    suspend fun onError(record: ConsumerRecord<K, V>, error: Exception)
}
