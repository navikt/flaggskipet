package no.nav.flaggskipet.db

import no.nav.flaggskipet.ApplicationState
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class DatabaseInitializer(
    private val dataSource: DataSource,
    private val applicationState: ApplicationState,
) {
    fun initialize() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        applicationState.ready = true
    }
}
