package no.kartverket.komreg.repositories

import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.util.Date
import javax.sql.DataSource

class KjoringRepo(
    private val dataSource: DataSource,
) {
    fun opprettKjoring(reguleringId: String): Kjoring {
        dataSource.connection.use { connection ->

            val skjemaStatement = connection.prepareStatement(
                "SELECT skjema FROM skjemaconfig",
            )

            val result = skjemaStatement.executeQuery()

            if (result.next()) {
                val skjema = result.getString("skjema") // Change here

                val insertStatement = connection.prepareStatement(
                    "INSERT INTO kjoring (regulering, start, status, skjema) VALUES (?, now(), 'OPPRETTET', ?)",
                    Statement.RETURN_GENERATED_KEYS,
                )
                insertStatement.setString(1, reguleringId)
                insertStatement.setString(2, skjema) // Add this line before executing the update
                insertStatement.executeUpdate()

                val generatedKeys = insertStatement.generatedKeys
                return if (generatedKeys.next()) {
                    val id = generatedKeys.getInt(1)
                    Kjoring(
                        id = id,
                        regulering = reguleringId,
                        start = null,
                        stop = null,
                        skjema = skjema,
                        status = Kjoringstatus.OPPRETTET,
                    )
                } else {
                    throw SQLException("Klarte ikke å opprette kjøring")
                }
            } else {
                throw RuntimeException("Fant ikke ledig skjema i skjemaconfig")
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
                    skjema = result.getString("skjema"),
                    status = enumValueOf<Kjoringstatus>(result.getString("status")),
                )
            } else {
                null
            }
        }
    }

    fun startKjoring(kjoringId: Int) {
        dataSource.connection.use { connection ->
            val updateStatement = connection.prepareStatement(
                "UPDATE kjoring SET status = 'KJØRER' WHERE id = ?",
            )

            updateStatement.setInt(2, kjoringId)

            updateStatement.executeUpdate()
        }
    }

    fun setStatusForKjøring(kjoringId: Int, status: Kjoringstatus) {
        dataSource.connection.use { connection ->
            val updateStatement = connection.prepareStatement(
                "UPDATE kjoring SET status = ? WHERE id = ?",
            )

            updateStatement.setString(1, status.toString())
            updateStatement.setInt(2, kjoringId)

            updateStatement.executeUpdate()
        }
    }

    fun getStatusForKjoringMedReguleringsId(reguleringsId: String): List<Kjoring> {
        val kjoringer = mutableListOf<Kjoring>()
        dataSource.connection.use { connection ->
            val prepareStatement = connection.prepareStatement("SELECT * FROM kjoring WHERE regulering = ?")

            prepareStatement.setString(1, reguleringsId)

            val result = prepareStatement.executeQuery()

            while (result.next()) {
                kjoringer.add(
                    Kjoring(
                        id = result.getInt("id"),
                        regulering = result.getString("regulering"),
                        start = result.getTimestamp("start"),
                        stop = result.getTimestamp("slutt"),
                        skjema = result.getString("skjema"),
                        status = enumValueOf<Kjoringstatus>(result.getString("status")),
                    ),
                )
            }
        }

        return kjoringer
    }

    fun getKjoringer(): List<Kjoring> {
        val kjoringer = mutableListOf<Kjoring>()
        dataSource.connection.use { connection ->
            val prepareStatement = connection.prepareStatement("SELECT * FROM kjoring")

            val result = prepareStatement.executeQuery()

            while (result.next()) {
                kjoringer.add(
                    Kjoring(
                        id = result.getInt("id"),
                        regulering = result.getString("regulering"),
                        start = result.getTimestamp("start"),
                        stop = result.getTimestamp("slutt"),
                        skjema = result.getString("skjema"),
                        status = enumValueOf<Kjoringstatus>(result.getString("status")),
                    ),
                )
            }
        }

        return kjoringer
    }

    fun getKjoring(kjoringId: Int): Kjoring? {
        dataSource.connection.use { connection ->
            val preparedStatement = connection.prepareStatement("SELECT * FROM kjoring WHERE id = ?")

            preparedStatement.setInt(1, kjoringId)

            val result = preparedStatement.executeQuery()

            return if (result.next()) {
                Kjoring(
                    id = result.getInt("id"),
                    regulering = result.getString("regulering"),
                    start = result.getTimestamp("start"),
                    stop = result.getTimestamp("slutt"),
                    skjema = result.getString("skjema"),
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
    val start: Date?,
    val stop: Date?,
    val skjema: String,
    val status: Kjoringstatus,
)

enum class Kjoringstatus(status: String) {
    OPPRETTET("OPPRETTET"),
    KJØRER("KJØRER"),
    STARTET_TILBAKEFØRING("STARTET_TILBAKEFØRING"),
    IKKE_TILBAKEFØRT("IKKE_TILBAKEFØRT"),
    STOPPET("STOPPET"),
    AVBRUTT("AVBRUTT"),
    TILBAKEFØRING_FEILET("TILBAKEFØRING_FEILET"),
    FULLFØRT_TILBAKEFØRING("FULLFØRT_TILBAKEFØRING"),
    FERDIG("FERDIG"),
}
