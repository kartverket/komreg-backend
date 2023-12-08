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

    fun hentMottakerSkjema(): MottakerSkjema {
        dataSource.connection.use { connection ->

            val mottakere = mutableListOf<MottakerSkjema>()
            val preparedStatement = connection.prepareStatement("SELECT * FROM mottakerskjema")

            val result = preparedStatement.executeQuery()

            while (result.next()) {
                mottakere.add(
                    MottakerSkjema(
                        id = result.getInt("id"),
                        mottaker = enumValueOf<Mottaker>(result.getString("mottaker")),
                        isFree = result.getBoolean("isfree"),
                        created_at = result.getTimestamp("created_at"),
                        updated_at = result.getTimestamp("updated_at"),
                    )
                )
            }


            if (mottakere.all { !it.isFree }) {
                throw RuntimeException("Alle mottakere er opptatt")
            }

            return mottakere.first { it.isFree }

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
                        status = enumValueOf<Kjoringstatus>(result.getString("status")),
                    ),
                )
            }
        }

        return kjoringer
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
    KJØRER("KJØRER"),
    STARTET_TILBAKEFØRING("STARTET_TILBAKEFØRING"),
    IKKE_TILBAKEFØRT("IKKE_TILBAKEFØRT"),
    STOPPET("STOPPET"),
    AVBRUTT("AVBRUTT"),
    TILBAKEFØRING_FEILET("TILBAKEFØRING_FEILET"),
    FULLFØRT_TILBAKEFØRING("FULLFØRT_TILBAKEFØRING"),
    FERDIG("FERDIG"),
}

data class MottakerSkjema(
    val id: Int,
    val mottaker: Mottaker,
    val isFree: Boolean,
    val created_at: Date,
    val updated_at: Date?
)

enum class Mottaker() {
    DB_MATRIKKEL_MOTTAKER1,
    DB_MATRIKKEL_MOTTAKER2
}
