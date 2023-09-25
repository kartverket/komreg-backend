package no.kartverket.komreg.repositories

import java.sql.DriverManager
import java.sql.Statement
import java.sql.Timestamp

class KjoringRepo(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) {
    fun insertAndRetrieveKjoringId(reguleringId: String): Int? {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
        try {
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
        } finally {
            connection.close()
        }
    }

    fun updateKjoringEndTime(kjoringId: Int) {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
        try {
            val endTime = Timestamp(System.currentTimeMillis())
            val updateStatement = connection.prepareStatement(
                "UPDATE kjoring SET slutt = ? WHERE id = ? AND slutt IS NULL",
            )
            updateStatement.setTimestamp(1, endTime)
            updateStatement.setInt(2, kjoringId)
            updateStatement.executeUpdate()
        } finally {
            connection.close()
        }
    }
}
