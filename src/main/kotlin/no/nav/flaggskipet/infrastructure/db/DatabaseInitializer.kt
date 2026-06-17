package no.nav.flaggskipet.infrastructure.db

import org.flywaydb.core.Flyway
import javax.sql.DataSource

class DatabaseInitializer(
    private val dataSource: DataSource,
) {
    fun initialize() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
