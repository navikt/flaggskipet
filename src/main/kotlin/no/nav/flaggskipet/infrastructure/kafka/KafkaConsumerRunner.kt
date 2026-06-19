package no.nav.flaggskipet.infrastructure.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

data class KafkaRecordMetadata(
    val topic: String,
    val partition: Int,
    val offset: Long,
)

enum class KafkaHandleResult {
    COMMIT,
}

fun interface KafkaMessageHandler<K, V> {
    fun handle(record: ConsumerRecord<K, V>): KafkaHandleResult
}

class KafkaConsumerRunner<K, V>(
    private val consumer: Consumer<K, V>,
    private val topics: List<String>,
    private val handler: KafkaMessageHandler<K, V>,
    private val pollTimeout: Duration = Duration.ofSeconds(1),
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    private val started = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    init {
        require(topics.isNotEmpty()) { "topics must not be empty" }
    }

    fun run() {
        check(!closed.get()) { "Kafka consumer runner is already closed" }
        check(running.compareAndSet(false, true)) { "Kafka consumer runner is already running" }
        started.set(true)

        try {
            consumer.subscribe(topics)

            while (running.get()) {
                val records = consumer.poll(pollTimeout)
                val offsetsToCommit = mutableMapOf<TopicPartition, OffsetAndMetadata>()

                for (record in records) {
                    try {
                        when (handler.handle(record)) {
                            KafkaHandleResult.COMMIT ->
                                offsetsToCommit[TopicPartition(record.topic(), record.partition())] =
                                    OffsetAndMetadata(record.offset() + 1)
                        }
                    } catch (error: Exception) {
                        val metadata = record.metadata()
                        logger.error(
                            "Kafka message handling failed for topic={}, partition={}, offset={}",
                            metadata.topic,
                            metadata.partition,
                            metadata.offset,
                            error,
                        )
                        throw error
                    }
                }

                if (offsetsToCommit.isNotEmpty()) {
                    consumer.commitSync(offsetsToCommit)
                }
            }
        } catch (error: WakeupException) {
            if (running.get()) {
                throw error
            }
        } finally {
            running.set(false)
            closeConsumer()
        }
    }

    fun stop() {
        if (running.compareAndSet(true, false)) {
            consumer.wakeup()
        }
    }

    override fun close() {
        if (started.get()) {
            stop()
        } else {
            closeConsumer()
        }
    }

    private fun ConsumerRecord<K, V>.metadata(): KafkaRecordMetadata = KafkaRecordMetadata(
        topic = topic(),
        partition = partition(),
        offset = offset(),
    )

    private fun closeConsumer() {
        if (closed.compareAndSet(false, true)) {
            consumer.close()
        }
    }
}
