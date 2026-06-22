package no.nav.flaggskipet.infrastructure.db.core

import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger(HealthIndicator::class.java)

class HealthIndicator(
    private val dataSource: DataSource,
) {
    fun isHealthy(): Boolean = try {
        dataSource.connection.use { connection ->
            connection.isValid(1)
        }
    } catch (ex: SQLException) {
        logger.warn("Database health check failed", ex)
        false
    }
}
