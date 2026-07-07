package no.nav.flaggskipet.infrastructure

data class HealthResult(
    val healthy: Boolean,
    val message: String,
)

interface HealthCheck {
    suspend fun check(): HealthResult
}
