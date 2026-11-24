package no.kartverket.komreg.repositories

import kotlinx.serialization.Serializable
import no.kartverket.komreg.Transformasjonsmotor
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.util.Date
import javax.sql.DataSource

class KjoringRepo(
    private val dataSource: DataSource,
) {
    fun insertAndRetrieveKjoringId(
        reguleringId: String,
        motor: Transformasjonsmotor = Transformasjonsmotor.LEGACY,
    ): Int? = dataSource.connection.use { insertAndRetrieveKjoringId(it, reguleringId, motor) }

    fun insertAndRetrieveKjoringId(
        connection: Connection,
        reguleringId: String,
        motor: Transformasjonsmotor,
    ): Int? {
        val insertStatement = connection.prepareStatement(
            "INSERT INTO kjoring (${motor.reguleringskolonne}, start, status, mottaker) VALUES (?, now(), 'KJØRER', ?)",
            Statement.RETURN_GENERATED_KEYS,
        )
        insertStatement.setString(1, reguleringId)
        insertStatement.setString(2, hentMottakerSkjema(connection).mottaker.toString())
        insertStatement.executeUpdate()

        val generatedKeys = insertStatement.generatedKeys
        return if (generatedKeys.next()) {
            generatedKeys.getInt(1)
        } else {
            null
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

    fun finnStoppetKjøringForRegulering(
        reguleringId: String,
        motor: Transformasjonsmotor = Transformasjonsmotor.LEGACY,
    ): Kjoring? = dataSource.connection.use { finnStoppetKjøringForRegulering(it, reguleringId, motor) }

    fun finnStoppetKjøringForRegulering(
        connection: Connection,
        reguleringId: String,
        motor: Transformasjonsmotor,
    ): Kjoring? {
        val preparedStatement = connection.prepareStatement(
            "SELECT * FROM kjoring WHERE ${motor.reguleringskolonne} = ? \n" +
                "AND status = 'STOPPET' \n" +
                "AND slutt IS NULL \n" +
                "ORDER BY start DESC \n" +
                "LIMIT 1;\n",
        )

        preparedStatement.setString(1, reguleringId)
        val result = preparedStatement.executeQuery()

        return if (result.next()) {
            result.toKjoring()
        } else {
            null
        }
    }

    fun setStatusForKjøring(kjoringId: Int, status: Kjoringstatus) {
        dataSource.connection.use { setStatusForKjøring(it, kjoringId, status) }
    }

    fun setStatusForKjøring(connection: Connection, kjoringId: Int, status: Kjoringstatus) {
        connection.prepareStatement("UPDATE kjoring SET status = ? WHERE id = ?").use { updateStatement ->
            updateStatement.setString(1, status.toString())
            updateStatement.setInt(2, kjoringId)
            updateStatement.executeUpdate()
        }
    }

    fun hentMottakerSkjema(): MottakerSkjema = dataSource.connection.use { hentMottakerSkjema(it) }

    private fun hentMottakerSkjema(connection: Connection): MottakerSkjema {
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

    fun settMottakerSkjema(mottaker: Mottaker) {
        dataSource.connection.use { connection ->
            try {
                connection.autoCommit = false

                val alleMottakere = connection.prepareStatement(
                    "UPDATE mottakerskjema SET isfree = FALSE, updated_at = ?"
                )
                alleMottakere.setTimestamp(1, Timestamp(System.currentTimeMillis()))
                alleMottakere.executeUpdate()

                val nyMottakerStatement = connection.prepareStatement(
                    "UPDATE mottakerskjema SET isfree = TRUE, updated_at = ? WHERE mottaker = ?"
                )

                nyMottakerStatement.setTimestamp(1, Timestamp(System.currentTimeMillis()))
                nyMottakerStatement.setString(2, mottaker.toString())
                nyMottakerStatement.executeUpdate()

                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            }
        }
    }

    fun getStatusForKjoringMedReguleringsId(
        reguleringsId: String,
        motor: Transformasjonsmotor = Transformasjonsmotor.LEGACY,
    ): List<Kjoring> = dataSource.connection.use { getStatusForKjoringMedReguleringsId(it, reguleringsId, motor) }

    fun getStatusForKjoringMedReguleringsId(
        connection: Connection,
        reguleringsId: String,
        motor: Transformasjonsmotor,
    ): List<Kjoring> {
        val kjoringer = mutableListOf<Kjoring>()
        val prepareStatement =
            connection.prepareStatement("SELECT * FROM kjoring WHERE ${motor.reguleringskolonne} = ?")

        prepareStatement.setString(1, reguleringsId)

        val result = prepareStatement.executeQuery()

        while (result.next()) {
            kjoringer.add(
                result.toKjoring(),
            )
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
                    result.toKjoring(),
                )
            }
        }

        return kjoringer
    }

    fun getKjoring(kjoringId: Int): Kjoring? {
        return dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM kjoring where id=?").use { prepareStatement ->
                prepareStatement.setInt(1, kjoringId)
                prepareStatement.executeQuery().use { result ->
                    val kjoring = if (result.next()) {
                        result.toKjoring()
                    } else {
                        null
                    }

                    if (result.next()) {
                        null
                    } else {
                        kjoring
                    }
                }
            }
        }
    }
}

private fun ResultSet.toKjoring() = Kjoring(
    id = getInt("id"),
    regulering = getString("regulering"),
    reformRegulering = getString("reform_regulering"),
    start = getTimestamp("start"),
    stop = getTimestamp("slutt"),
    status = enumValueOf<Kjoringstatus>(getString("status")),
    mottaker = enumValueOf<Mottaker>(getString("mottaker")),
)

/** Kolonnen i `kjoring` som peker på reguleringen for [Transformasjonsmotor]. */
private val Transformasjonsmotor.reguleringskolonne: String
    get() = when (this) {
        Transformasjonsmotor.LEGACY -> "regulering"
        Transformasjonsmotor.REFORM -> "reform_regulering"
    }

/** En kjøring har enten [regulering] (`legacy`) eller [reformRegulering] (`reform`). */
data class Kjoring(
    val id: Int,
    val regulering: String?,
    val reformRegulering: String?,
    val start: Date,
    val stop: Date?,
    val status: Kjoringstatus,
    val mottaker: Mottaker
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

@Serializable
enum class Mottaker() {
    DB_MATRIKKEL_MOTTAKER1,
    DB_MATRIKKEL_MOTTAKER2
}
