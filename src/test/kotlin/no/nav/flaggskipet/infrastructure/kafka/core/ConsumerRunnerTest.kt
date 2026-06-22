package no.nav.flaggskipet.infrastructure.kafka.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TOPIC = "teamsykmelding.syfo-sendt-sykmelding"

class ConsumerRunnerTest :
    FunSpec({
        test("runner commits handled records and stops cleanly") {
            val partition = TopicPartition(TOPIC, 0)
            val consumer = RecordingMockConsumer()
            val handledOffsets = mutableListOf<Long>()
            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler { record ->
                    handledOffsets += record.offset()
                },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 0L, "key", "value"))
            }
            consumer.schedulePollTask {
                runner.stop()
            }

            runner.start()
            runner.join()

            handledOffsets.shouldContainExactly(0L)
            consumer.committedOffsets[partition]?.offset()!!.shouldBeExactly(1L)
            consumer.closed() shouldBe true
        }

        test("runner commits a whole poll batch once at the highest offset") {
            val partition = TopicPartition(TOPIC, 0)
            val consumer = RecordingMockConsumer()
            val handledOffsets = mutableListOf<Long>()
            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler { record ->
                    handledOffsets += record.offset()
                },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 0L, "key", "value-0"))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 1L, "key", "value-1"))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 2L, "key", "value-2"))
            }
            consumer.schedulePollTask {
                runner.stop()
            }

            runner.start()
            runner.join()

            handledOffsets.shouldContainExactly(0L, 1L, 2L)
            consumer.committedOffsets[partition]?.offset()!!.shouldBeExactly(3L)
            consumer.commitCount.shouldBeExactly(1L)
        }

        test("runner does not commit records when the handler fails") {
            val partition = TopicPartition(TOPIC, 0)
            val consumer = RecordingMockConsumer()

            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                maxRetries = 0,
                handler = MessageHandler { error("boom") },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 0L, "key", "value"))
                // The handler throws on this batch; stop before any retry can re-poll.
                runner.stop()
            }

            runner.start()
            runner.join()

            consumer.committedOffsets[partition] shouldBe null
        }

        test("runner rebuilds the consumer and recovers after a transient failure") {
            val partition = TopicPartition(TOPIC, 0)
            val createdConsumers = mutableListOf<RecordingMockConsumer>()
            val handledOffsets = mutableListOf<Long>()
            val attempts = AtomicInteger(0)

            lateinit var runner: ConsumerRunner<String, String>
            runner = ConsumerRunner(
                consumerFactory = {
                    RecordingMockConsumer().also { consumer ->
                        createdConsumers += consumer
                        consumer.schedulePollTask {
                            consumer.rebalance(listOf(partition))
                            consumer.updateBeginningOffsets(mapOf(partition to 0L))
                            consumer.addRecord(ConsumerRecord(TOPIC, 0, 0L, "key", "value"))
                        }
                    }
                },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                initialBackoff = Duration.ofMillis(1),
                maxBackoff = Duration.ofMillis(1),
                maxRetries = 0,
                handler = MessageHandler { record ->
                    // Fail on the very first attempt (transient), then succeed on the restart.
                    if (attempts.getAndIncrement() == 0) {
                        error("transient boom")
                    }
                    handledOffsets += record.offset()
                    runner.stop()
                },
            )

            runner.start()
            runner.join(Duration.ofSeconds(5)) shouldBe true

            handledOffsets.shouldContainExactly(0L)
            (createdConsumers.size >= 2) shouldBe true
            createdConsumers.all { it.closed() } shouldBe true
        }

        test("runner invokes onFatalError and stops on a fatal error") {
            val consumer = RecordingMockConsumer()
            var capturedError: Throwable? = null

            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler { throw ConfigException("bad config") },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(TopicPartition(TOPIC, 0)))
                consumer.updateBeginningOffsets(mapOf(TopicPartition(TOPIC, 0) to 0L))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 0L, "key", "value"))
            }

            runner.start { error -> capturedError = error }
            runner.join(Duration.ofSeconds(5)) shouldBe true

            (capturedError is ConfigException) shouldBe true
            consumer.closed() shouldBe true
        }

        test("runner does not invoke onFatalError on a clean stop") {
            val consumer = RecordingMockConsumer()
            var fatalCount = 0

            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler {},
            )

            consumer.schedulePollTask {
                runner.stop()
            }

            runner.start { fatalCount++ }
            runner.join()

            fatalCount shouldBe 0
            consumer.closed() shouldBe true
        }

        test("close without start does nothing and never builds a consumer") {
            val built = AtomicInteger(0)
            val runner = ConsumerRunner(
                consumerFactory = {
                    built.incrementAndGet()
                    RecordingMockConsumer()
                },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler {},
            )

            runner.stop()

            built.get() shouldBe 0
        }

        test("close after start joins the loop and closes the consumer exactly once") {
            val consumer = RecordingMockConsumer()
            val polling = CountDownLatch(1)
            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler {},
            )

            // Signal once the consumer is actually polling so stop() cannot race the loop startup.
            consumer.schedulePollTask { polling.countDown() }

            runner.start()
            polling.await(5, TimeUnit.SECONDS) shouldBe true
            runner.stop()
            runner.join()

            consumer.closed() shouldBe true
            consumer.closeCount.shouldBeExactly(1L)
        }

        test("join returns false when the loop does not stop within the timeout") {
            val consumer = RecordingMockConsumer()
            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler {},
            )

            runner.start()
            val stoppedInTime = runner.join(Duration.ofMillis(50))

            stoppedInTime shouldBe false

            runner.stop()
            runner.join()
            consumer.closed() shouldBe true
        }

        test("join returns true after a clean stop") {
            val consumer = RecordingMockConsumer()
            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler {},
            )

            consumer.schedulePollTask {
                runner.stop()
            }

            runner.start()
            val stoppedInTime = runner.join(Duration.ofSeconds(2))

            stoppedInTime shouldBe true
            consumer.closed() shouldBe true
        }

        test("start twice throws") {
            val consumer = RecordingMockConsumer()
            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                handler = MessageHandler {},
            )

            runner.start()
            runCatching { runner.start() }.isFailure shouldBe true
            runner.stop()
        }

        test("handler succeeds after retrying on transient failure") {
            val partition = TopicPartition(TOPIC, 0)
            val consumer = RecordingMockConsumer()
            val handledOffsets = mutableListOf<Long>()
            val attemptCount = AtomicInteger(0)

            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                maxRetries = 2,
                retryBackoff = Duration.ofMillis(1),
                handler = MessageHandler { record ->
                    if (attemptCount.getAndIncrement() < 2) {
                        error("transient boom")
                    }
                    handledOffsets += record.offset()
                },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 0L, "key", "value"))
            }
            consumer.schedulePollTask {
                runner.stop()
            }

            runner.start()
            runner.join()

            handledOffsets.shouldContainExactly(0L)
            consumer.committedOffsets[partition]?.offset()!!.shouldBeExactly(1L)
        }

        test("handler exhausts retries and invokes error handler") {
            val partition = TopicPartition(TOPIC, 0)
            val consumer = RecordingMockConsumer()
            val captured = mutableListOf<Pair<ConsumerRecord<String, String>, Exception>>()

            val runner = ConsumerRunner(
                consumerFactory = { consumer },
                topics = listOf(TOPIC),
                pollTimeout = Duration.ofMillis(1),
                maxRetries = 2,
                retryBackoff = Duration.ofMillis(1),
                handler = MessageHandler { error("boom") },
                errorHandler = ConsumerErrorHandler { record, error ->
                    captured += record to error
                },
            )

            consumer.schedulePollTask {
                consumer.rebalance(listOf(partition))
                consumer.updateBeginningOffsets(mapOf(partition to 0L))
                consumer.addRecord(ConsumerRecord(TOPIC, 0, 0L, "key", "value"))
            }
            consumer.schedulePollTask {
                runner.stop()
            }

            runner.start()
            runner.join()

            captured.size shouldBe 1
            captured[0].first.offset() shouldBe 0L
            captured[0].second.message shouldBe "boom"
        }
    })

private class RecordingMockConsumer : MockConsumer<String, String>("earliest") {
    val committedOffsets: MutableMap<TopicPartition, OffsetAndMetadata> = mutableMapOf()
    var commitCount: Long = 0L
        private set
    var closeCount: Long = 0L
        private set

    override fun commitSync(offsets: Map<TopicPartition, OffsetAndMetadata>) {
        commitCount++
        committedOffsets.putAll(offsets)
        super.commitSync(offsets)
    }

    override fun close() {
        closeCount++
        super.close()
    }
}
