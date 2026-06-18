package no.nav.flaggskipet.infrastructure.db

import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val jdbcUrl: String,
) {
    override fun toString(): String = "DatabaseConfig(host=$host, port=$port, database=$database, username=$username, password=***)"

    companion object {
        fun fromConfig(config: ApplicationConfig): DatabaseConfig {
            val values = REQUIRED_PATHS.associateWith(config::value)
            val port = values.getValue(PORT_PATH).toIntOrNull()
            val errors = REQUIRED_PATHS.mapNotNull { path ->
                path.takeIf { values.getValue(it).isBlank() }?.let { "$it must be set" }
            } + when {
                values.getValue(PORT_PATH).isBlank() -> emptyList()
                port == null -> listOf("$PORT_PATH must be an integer")
                port <= 0 -> listOf("$PORT_PATH must be greater than zero")
                else -> emptyList()
            }

            check(errors.isEmpty()) {
                "Invalid database configuration: ${errors.joinToString(", ")}"
            }

            return DatabaseConfig(
                host = values.getValue(HOST_PATH),
                port = port!!,
                database = values.getValue(DATABASE_PATH),
                username = values.getValue(USERNAME_PATH),
                password = values.getValue(PASSWORD_PATH),
                jdbcUrl = "jdbc:${values.getValue(URL_PATH)}",
            )
        }

        private val REQUIRED_PATHS = listOf(
            HOST_PATH,
            PORT_PATH,
            DATABASE_PATH,
            USERNAME_PATH,
            PASSWORD_PATH,
            URL_PATH,
        )

        private const val HOST_PATH = "database.host"
        private const val PORT_PATH = "database.port"
        private const val DATABASE_PATH = "database.name"
        private const val USERNAME_PATH = "database.username"
        private const val PASSWORD_PATH = "database.password"
        private const val URL_PATH = "database.url"
    }
}

private fun ApplicationConfig.value(path: String): String = propertyOrNull(path)
    ?.getString()
    ?: ""
