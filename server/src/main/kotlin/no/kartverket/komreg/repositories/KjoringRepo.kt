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
                "INSERT INTO kjoring (regulering, start, status) VALUES (?, now(), 'KJØRER')",
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
                "UPDATE kjoring SET slutt = ?, status = ? WHERE id = ? AND slutt IS NULL",
            )
            updateStatement.setTimestamp(1, endTime)
            updateStatement.setString(2, Kjoringstatus.FERDIG.toString())
            updateStatement.setInt(3, kjoringId)
            updateStatement.executeUpdate()
        }
    }

    fun finnStoppetKjøringForRegulering(reguleringId: String): Kjoring? {
        dataSource.connection.use { connection ->
            val preparedStatement = connection.prepareStatement(
                "SELECT * FROM kjoring WHERE regulering = ? \n" +
                    "AND status = 'TILBAKEFØRING_FEILET' \n" +
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

    fun setStatusForKjøring(kjoringId: Int, status: Kjoringstatus) {
        println("status $status")
        dataSource.connection.use { connection ->
            val updateStatement = connection.prepareStatement(
                "UPDATE kjoring SET status = ? WHERE id = ?",
            )

            updateStatement.setString(1, status.toString())
            updateStatement.setInt(2, kjoringId)

            updateStatement.executeUpdate()
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
    STARTET_TILBAKEFØRING("STARTET_TILBAKEFØRING"),
    IKKE_TILBAKEFØRT("IKKE_TILBAKEFØRT"),
    STOPPET("STOPPET"),
    TILBAKEFØRING_FEILET("TILBAKEFØRING_FEILET"),
    FULLFØRT_TILBAKEFØRING("FULLFØRT_TILBAKEFØRING"),
    FERDIG("FERDIG"),
}
