package no.nav.flaggskipet.infrastructure.kafka

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class KafkaConsumerRunnerTest :
    FunSpec({
        test("runner commits handled records and stops cleanly") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val partition = TopicPartition(topic, 0)
            val consumer = RecordingMockConsumer()
            val handledOffsets = mutableListOf<Long>()
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(1),
                handler = KafkaMessageHandler { record ->
                    handledOffsets += record.offset()
                    KafkaHandleResult.COMMIT
                },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(topic, 0, 0L, "key", "value"))
            }
            consumer.schedulePollTask {
                runner.stop()
            }

            runner.run()

            handledOffsets.shouldContainExactly(0L)
            consumer.committedOffsets[partition]?.offset()!!.shouldBeExactly(1L)
            consumer.closed() shouldBe true
        }

        test("runner commits a whole poll batch once at the highest offset") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val partition = TopicPartition(topic, 0)
            val consumer = RecordingMockConsumer()
            val handledOffsets = mutableListOf<Long>()
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(1),
                handler = KafkaMessageHandler { record ->
                    handledOffsets += record.offset()
                    KafkaHandleResult.COMMIT
                },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(topic, 0, 0L, "key", "value-0"))
                consumer.addRecord(ConsumerRecord(topic, 0, 1L, "key", "value-1"))
                consumer.addRecord(ConsumerRecord(topic, 0, 2L, "key", "value-2"))
            }
            consumer.schedulePollTask {
                runner.stop()
            }

            runner.run()

            handledOffsets.shouldContainExactly(0L, 1L, 2L)
            consumer.committedOffsets[partition]?.offset()!!.shouldBeExactly(3L)
            consumer.commitCount.shouldBeExactly(1L)
        }

        test("runner does not commit records when handler fails") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val partition = TopicPartition(topic, 0)
            val consumer = RecordingMockConsumer()

            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(1),
                handler = KafkaMessageHandler {
                    error("boom")
                },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(topic, 0, 0L, "key", "value"))
            }

            runCatching { runner.run() }.isFailure shouldBe true

            consumer.committedOffsets[partition] shouldBe null
        }

        test("close without run closes the underlying consumer") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val consumer = RecordingMockConsumer()
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(1),
                handler = KafkaMessageHandler { KafkaHandleResult.COMMIT },
            )

            runner.close()

            consumer.closed() shouldBe true
        }

        test("run after close throws") {
            val topic = "teamsykmelding.syfo-sendt-sykmelding"
            val consumer = RecordingMockConsumer()
            val runner = KafkaConsumerRunner(
                consumer = consumer,
                topics = listOf(topic),
                pollTimeout = Duration.ofMillis(1),
                handler = KafkaMessageHandler { KafkaHandleResult.COMMIT },
            )

            runner.close()

            runCatching { runner.run() }.isFailure shouldBe true
        }
    })

private class RecordingMockConsumer : MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
    val committedOffsets: MutableMap<TopicPartition, OffsetAndMetadata> = mutableMapOf()
    var commitCount: Long = 0L
        private set

    override fun commitSync(offsets: Map<TopicPartition, OffsetAndMetadata>) {
        commitCount++
        committedOffsets.putAll(offsets)
        super.commitSync(offsets)
    }
}
