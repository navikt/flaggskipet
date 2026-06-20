package no.nav.flaggskipet.infrastructure.kafka

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.time.delay
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class KafkaConsumerLifecycleTest :
    FunSpec({
        test("unexpected consumer failure closes consumer") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val partition = TopicPartition(topic, 0)
            val consumer = MockConsumer<String, ByteArray?>("earliest")
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(1),
                handler = KafkaMessageHandler {
                    error("boom")
                },
            )
            val lifecycle = KafkaConsumerLifecycle(
                consumerName = KafkaConsumerName.SYKMELDING,
                runner = runner,
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(topic, 0, 0L, "key", "value".toByteArray()))
            }

            lifecycle.start()

            awaitUntil(timeout = Duration.ofSeconds(2)) {
                consumer.closed()
            }
        }

        test("controlled stop closes consumer") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val consumer = MockConsumer<String, ByteArray?>("earliest")
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(10),
                handler = KafkaMessageHandler { KafkaHandleResult.COMMIT },
            )
            val lifecycle = KafkaConsumerLifecycle(
                consumerName = KafkaConsumerName.SYKMELDING,
                runner = runner,
            )

            lifecycle.start()
            awaitUntil(timeout = Duration.ofSeconds(2)) {
                consumer.subscription().contains(topic)
            }
            lifecycle.stop(Duration.ofSeconds(1))

            consumer.closed() shouldBe true
        }
    })

private suspend fun awaitUntil(
    timeout: Duration,
    condition: () -> Boolean,
) {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (!condition()) {
        if (System.nanoTime() >= deadline) {
            error("Condition was not met within $timeout")
        }
        delay(Duration.ofMillis(10))
    }
}
