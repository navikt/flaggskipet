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
            fun value(key: String): String = config.propertyOrNull("database.$key")?.getString().orEmpty()

            val host = value("host")
            val port = value("port")
            val name = value("name")
            val username = value("username")
            val password = value("password")
            val url = value("url")

            val errors = buildList {
                if (host.isBlank()) add("database.host must be set")
                when {
                    port.isBlank() -> add("database.port must be set")
                    (port.toIntOrNull() ?: 0) <= 0 -> add("database.port must be a positive integer")
                }
                if (name.isBlank()) add("database.name must be set")
                if (username.isBlank()) add("database.username must be set")
                if (password.isBlank()) add("database.password must be set")
                if (url.isBlank()) add("database.url must be set")
            }

            check(errors.isEmpty()) {
                "Invalid database configuration: ${errors.joinToString(", ")}"
            }

            return DatabaseConfig(
                host = host,
                port = port.toInt(),
                database = name,
                username = username,
                password = password,
                jdbcUrl = "jdbc:$url",
            )
        }
    }
}
