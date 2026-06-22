package no.nav.flaggskipet.infrastructure.db.core

import org.flywaydb.core.Flyway
import javax.sql.DataSource

class Initializer(
    private val dataSource: DataSource,
) {
    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
