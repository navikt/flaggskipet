package no.nav.flaggskipet.bootstrap

data class ApplicationState(
    @Volatile var alive: Boolean = true,
    @Volatile var ready: Boolean = false,
)
