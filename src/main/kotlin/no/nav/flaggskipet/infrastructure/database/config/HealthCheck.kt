package no.nav.flaggskipet.infrastructure.database.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.flaggskipet.infrastructure.HealthCheck
import no.nav.flaggskipet.infrastructure.HealthResult
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger(DataSourceHealthCheck::class.java)

fun dataSourceHealthCheck(dataSource: DataSource): HealthCheck = DataSourceHealthCheck(dataSource)

private class DataSourceHealthCheck(
    private val dataSource: DataSource,
) : HealthCheck {

    override suspend fun check(): HealthResult = withContext(Dispatchers.IO) {
        runCatching {
            dataSource.connection.use { connection ->
                HealthResult(
                    healthy = connection.isValid(1),
                    message = "Database",
                )
            }
        }.getOrElse { exception ->
            logger.error("Database health check failed", exception)

            HealthResult(
                healthy = false,
                message = "Database unavailable",
            )
        }
    }
}
