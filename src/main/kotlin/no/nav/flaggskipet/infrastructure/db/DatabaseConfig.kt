package no.nav.flaggskipet.infrastructure.db

import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
) {
    fun jdbcUrl(): String = "jdbc:postgresql://$host:$port/$database"

    override fun toString(): String = "DatabaseConfig(host=$host, port=$port, database=$database, username=$username, password=***)"

    companion object {
        fun fromConfig(config: ApplicationConfig): DatabaseConfig = DatabaseConfig(
            host = config.required("database.host"),
            port = config.required("database.port").toIntOrNull()
                ?: error("database.port must be an integer"),
            database = config.required("database.name"),
            username = config.required("database.username"),
            password = config.required("database.password"),
        )
    }
}

private fun ApplicationConfig.required(path: String): String = propertyOrNull(path)
    ?.getString()
    ?.takeIf { it.isNotBlank() }
    ?: error("$path must be set")
