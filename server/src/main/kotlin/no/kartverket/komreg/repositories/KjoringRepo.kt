package no.kartverket.komreg.repositories

import java.sql.Statement
import java.sql.Timestamp
import javax.sql.DataSource

class KjoringRepo(
    private val dataSource: DataSource
) {
    fun insertAndRetrieveKjoringId(reguleringId: String): Int? {
        dataSource.connection.use { connection ->
            val insertStatement = connection.prepareStatement(
                "INSERT INTO kjoring (regulering, start) VALUES (?, now())",
                Statement.RETURN_GENERATED_KEYS,
            )
            insertStatement.setString(1, reguleringId)
            insertStatement.executeUpdate()

            val generatedKeys = insertStatement.generatedKeys
            return if (generatedKeys.next()) {
                generatedKeys.getInt(1)
            } else {
                null
            }
        }
    }

    fun updateKjoringEndTime(kjoringId: Int) {
        dataSource.connection.use { connection ->
            val endTime = Timestamp(System.currentTimeMillis())
            val updateStatement = connection.prepareStatement(
                "UPDATE kjoring SET slutt = ? WHERE id = ? AND slutt IS NULL",
            )
            updateStatement.setTimestamp(1, endTime)
            updateStatement.setInt(2, kjoringId)
            updateStatement.executeUpdate()
        }
    }
}
