package no.nav.flaggskippet

data class ApplicationState(
    @Volatile var alive: Boolean = true,
    @Volatile var ready: Boolean = true,
)
