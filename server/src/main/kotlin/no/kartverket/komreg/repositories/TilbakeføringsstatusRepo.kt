package no.kartverket.komreg.repositories

import no.kartverket.komreg.integration.spi.EntitySink
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

data class TilbakeføringsstatusForSink(
    val sinkId: String,
    val kjoringId: Int,
    val opprettinger: Status,
    val endringer: Status,
    val createdAt: Timestamp,
    val updatedAt: Timestamp?,

) {
    enum class Status {
        IKKE_STARTET,
        TILBAKEFØRER,
        FEILET,
        FERDIG,
    }
}

class TilbakeføringsstatusRepo(private val dataSource: DataSource) {

    fun createTilbakeføringsstatusForKjoring(kjoringId: Int, entitySinks: List<EntitySink>) {
        dataSource.connection.use { connection ->

            entitySinks.forEach { sink ->
                val insertStatement =
                    connection.prepareStatement("INSERT INTO tilbakeføringsstatus (id, kjoringid, sink, opprettinger, endringer ) VALUES (?, ?, ?, ?, ?)")
                insertStatement.setString(1, UUID.randomUUID().toString())
                insertStatement.setInt(2, kjoringId)
                insertStatement.setString(3, sink.id)
                insertStatement.setString(4, TilbakeføringsstatusForSink.Status.IKKE_STARTET.toString())
                insertStatement.setString(5, TilbakeføringsstatusForSink.Status.IKKE_STARTET.toString())

                insertStatement.executeUpdate()
            }
        }
    }

    fun getTilbakeføringsstatusForKjøringId(kjoringId: Int): List<TilbakeføringsstatusForSink>? {
        val tilbakeføringsstatuser = mutableListOf<TilbakeføringsstatusForSink>()
        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement("SELECT * FROM tilbakeføringsstatus WHERE kjoringid = ?")
            selectStatement.setInt(1, kjoringId)

            val sinkConfigResultSet = selectStatement.executeQuery()
            while (sinkConfigResultSet.next()) {
                tilbakeføringsstatuser.add(
                    TilbakeføringsstatusForSink(
                        sinkId = sinkConfigResultSet.getString("sink"),
                        kjoringId = sinkConfigResultSet.getInt("kjoringId"),
                        opprettinger = enumValueOf<TilbakeføringsstatusForSink.Status>(sinkConfigResultSet.getString("opprettinger")),
                        endringer = enumValueOf<TilbakeføringsstatusForSink.Status>(sinkConfigResultSet.getString("endringer")),
                        createdAt = sinkConfigResultSet.getTimestamp("created_at"),
                        updatedAt = sinkConfigResultSet.getTimestamp("updated_at"),
                    ),
                )
            }
        }
        return if (tilbakeføringsstatuser.isEmpty()) null else tilbakeføringsstatuser
    }

    fun setTilbakeføringsStatusForSink(
        sink: EntitySink,
        status: TilbakeføringsstatusForSink.Status,
        kjoringId: Int,
        erOppretting: Boolean,
    ) {
        val updatedAt = Timestamp(System.currentTimeMillis())
        val statusField = if (erOppretting) "opprettinger" else "endringer"

        dataSource.connection.use { connection ->
            val updateStatement =
                connection.prepareStatement("UPDATE tilbakeføringsstatus SET $statusField = ?, updated_at = ? WHERE kjoringid = ? AND sink = ?")

            updateStatement.setString(1, status.toString())
            updateStatement.setTimestamp(2, updatedAt)
            updateStatement.setInt(3, kjoringId)
            updateStatement.setString(4, sink.id)

            updateStatement.executeUpdate()
        }
    }

    fun hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId: Int): List<String> {
        val sinkIder = mutableListOf<String>()

        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement(
                    "SELECT sink FROM tilbakeføringsstatus WHERE kjoringid = ? AND (opprettinger = 'FEILET' OR opprettinger = 'IKKE_STARTET' OR opprettinger = 'TILBAKEFØRER')",
                )
            selectStatement.setInt(1, kjoringId)

            val sinkIdResultSet = selectStatement.executeQuery()
            while (sinkIdResultSet.next()) {
                sinkIder.add(sinkIdResultSet.getString("sink"))
            }
        }
        return sinkIder
    }

    fun hentIkkeStartedeTilbakeføringerForErstattendeEntiteter(kjoringId: Int): List<String> {
        val sinkIder = mutableListOf<String>()
        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement("SELECT sink FROM tilbakeføringsstatus  WHERE kjoringid = ? AND (endringer = 'FEILET' OR endringer = 'IKKE_STARTET')")
            selectStatement.setInt(1, kjoringId)

            val sinkIdResultSet = selectStatement.executeQuery()
            while (sinkIdResultSet.next()) {
                sinkIder.add(sinkIdResultSet.getString("sink"))
            }
        }
        return sinkIder
    }
}
