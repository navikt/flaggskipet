package no.nav.flaggskipet

data class ApplicationState(
    @Volatile var alive: Boolean = true,
    @Volatile var ready: Boolean = true,
)
