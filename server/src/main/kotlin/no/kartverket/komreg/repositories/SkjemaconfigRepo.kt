package no.kartverket.komreg.repositories

import javax.sql.DataSource

class SkjemaconfigRepo(private val dataSource: DataSource) {
    fun updateSkjema(skjema: String) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement("UPDATE skjemaconfig SET skjema = ?")
            statement.setString(1, skjema)
            statement.executeUpdate()
        }
    }
}
