package no.nav.flaggskipet.infrastructure.db

import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

class DatabaseHealthIndicator(
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

    private companion object {
        private val logger = LoggerFactory.getLogger(DatabaseHealthIndicator::class.java)
    }
}
