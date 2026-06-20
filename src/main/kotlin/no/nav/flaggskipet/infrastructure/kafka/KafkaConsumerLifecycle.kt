package no.nav.flaggskipet.infrastructure.kafka

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeoutOrNull
import no.nav.flaggskipet.bootstrap.ApplicationState
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class KafkaConsumerLifecycle<K, V>(
    private val consumerName: String,
    private val runner: KafkaConsumerRunner<K, V>,
    private val applicationState: ApplicationState,
    private val coroutineScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("$consumerName-kafka-consumer"),
    ),
) {
    private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    private val consumerJob = AtomicReference<Job?>()

    fun start() {
        check(consumerJob.get() == null) { "$consumerName Kafka consumer is already started" }

        val job = coroutineScope.launch {
            try {
                runner.run()
            } catch (error: Throwable) {
                applicationState.alive = false
                applicationState.ready = false
                logger.error(
                    "{} Kafka consumer stopped unexpectedly with exceptionType={}",
                    consumerName,
                    error.technicalType(),
                )
            }
        }

        check(consumerJob.compareAndSet(null, job)) {
            "$consumerName Kafka consumer is already started"
        }
    }

    fun stop(timeout: Duration) {
        val job = consumerJob.getAndSet(null) ?: return
        runner.stop()
        val stoppedWithinTimeout = runBlocking {
            withTimeoutOrNull(timeout) {
                job.join()
            } != null
        }

        if (!stoppedWithinTimeout) {
            logger.warn("{} Kafka consumer did not stop within {} ms", consumerName, timeout.toMillis())
        }
        coroutineScope.cancel()
    }

    private fun Throwable.technicalType(): String = javaClass.name
}
