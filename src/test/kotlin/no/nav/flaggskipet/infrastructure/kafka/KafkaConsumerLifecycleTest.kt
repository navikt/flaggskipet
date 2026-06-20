package no.nav.flaggskipet.infrastructure.kafka

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import no.nav.flaggskipet.bootstrap.ApplicationState
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class KafkaConsumerLifecycleTest :
    FunSpec({
        test("unexpected consumer failure marks application as not alive and not ready") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val partition = TopicPartition(topic, 0)
            val consumer = MockConsumer<String, ByteArray?>(OffsetResetStrategy.EARLIEST)
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(1),
                handler = KafkaMessageHandler {
                    error("boom")
                },
            )
            val applicationState = ApplicationState(alive = true, ready = true)
            val lifecycle = KafkaConsumerLifecycle(
                consumerName = "sykmelding",
                runner = runner,
                applicationState = applicationState,
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(topic, 0, 0L, "key", "value".toByteArray()))
            }

            lifecycle.start()

            awaitUntil(timeout = Duration.ofSeconds(2)) {
                !applicationState.alive && !applicationState.ready
            }

            applicationState.alive shouldBe false
            applicationState.ready shouldBe false
        }

        test("controlled stop keeps application alive") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val consumer = MockConsumer<String, ByteArray?>(OffsetResetStrategy.EARLIEST)
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(10),
                handler = KafkaMessageHandler { KafkaHandleResult.COMMIT },
            )
            val applicationState = ApplicationState(alive = true, ready = true)
            val lifecycle = KafkaConsumerLifecycle(
                consumerName = "sykmelding",
                runner = runner,
                applicationState = applicationState,
            )

            lifecycle.start()
            awaitUntil(timeout = Duration.ofSeconds(2)) {
                consumer.subscription().contains(topic)
            }
            lifecycle.stop(Duration.ofSeconds(1))

            applicationState.alive shouldBe true
            applicationState.ready shouldBe true
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
        delay(10)
    }
}
