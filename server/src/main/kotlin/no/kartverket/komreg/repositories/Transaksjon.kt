package no.kartverket.komreg.repositories

import java.sql.Connection
import javax.sql.DataSource

/** Kjører [block] i én transaksjon som committes hvis [block] fullfører, og rulles tilbake ellers. */
fun <T> DataSource.iTransaksjon(block: (Connection) -> T): T =
    connection.use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
