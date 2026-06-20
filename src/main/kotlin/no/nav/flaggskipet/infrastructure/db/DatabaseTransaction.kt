package no.nav.flaggskipet.infrastructure.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class DatabaseTransaction(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun <T> run(block: () -> T): T = withContext(dispatcher) {
        transaction(database) {
            block()
        }
    }
}
