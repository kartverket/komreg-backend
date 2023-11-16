package no.kartverket.komreg.repositories

import java.sql.Statement
import java.sql.Timestamp
import java.util.Date
import javax.sql.DataSource

class KjoringRepo(
    private val dataSource: DataSource,
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

    fun finnStoppetKjøringForRegulering(reguleringId: String): Kjoring? {
        dataSource.connection.use { connection ->
            val preparedStatement = connection.prepareStatement(
                "SELECT * \n" +
                    "FROM kjoring \n" +
                    "WHERE regulering = ? \n" +
                    "AND status = 'STOPPET' \n" +
                    "AND slutt IS NULL \n" +
                    "ORDER BY start DESC \n" +
                    "LIMIT 1;\n",
            )

            preparedStatement.setString(1, reguleringId)
            val result = preparedStatement.executeQuery()

            return if (result.next()) {
                Kjoring(
                    id = result.getInt("id"),
                    regulering = result.getString("regulering"),
                    start = result.getTimestamp("start"),
                    stop = result.getTimestamp("slutt"),
                    status = enumValueOf<Kjoringstatus>(result.getString("status")),
                )
            } else {
                null
            }
        }
    }
}

data class Kjoring(
    val id: Int,
    val regulering: String,
    val start: Date,
    val stop: Date?,
    val status: Kjoringstatus,
)

enum class Kjoringstatus(status: String) {
    KJORER("KJØRER"),
    STOPPET("STOPPET"),
    FEILET("FEILET"),
    FERDIG("FERDIG"),
}
