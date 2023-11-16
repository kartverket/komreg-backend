package no.kartverket.komreg.repositories

import no.kartverket.komreg.integration.spi.EntitySink
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

data class SinkConfig(
    val sinkId: String,
    val kjoringId: Int,
    val opprettinger: Boolean?,
    val endringer: Boolean?,
    val createdAt: Timestamp,
    val updatedAt: Timestamp?,
)

class TilbakeføringsstatusRepo(private val dataSource: DataSource) {

    fun createInitialTilbakeføringsstatus(kjoringId: Int, entitySinks: List<EntitySink>) {
        dataSource.connection.use { connection ->

            entitySinks.forEach { sink ->
                val insertStatement =
                    connection.prepareStatement("INSERT INTO tilbakeføringsstatus (id, kjoringid, sink ) VALUES (?, ?, ?)")
                insertStatement.setString(1, UUID.randomUUID().toString())
                insertStatement.setInt(2, kjoringId)
                insertStatement.setString(3, sink.id)

                insertStatement.executeUpdate()
            }
        }
    }

    fun getTilbakeføringsstatusForKjøringId(kjoringId: Int): List<SinkConfig>? {
        val configs = mutableListOf<SinkConfig>()
        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement("SELECT * FROM tilbakeføringsstatus WHERE kjoringid = ?")
            selectStatement.setInt(1, kjoringId)

            val sinkConfigResultSet = selectStatement.executeQuery()
            while (sinkConfigResultSet.next()) {
                configs.add(
                    SinkConfig(
                        sinkId = sinkConfigResultSet.getString("sink"),
                        kjoringId = sinkConfigResultSet.getInt("kjoringId"),
                        opprettinger = sinkConfigResultSet.getBoolean("opprettinger"),
                        endringer = sinkConfigResultSet.getBoolean("endringer"),
                        createdAt = sinkConfigResultSet.getTimestamp("created_at"),
                        updatedAt = sinkConfigResultSet.getTimestamp("updated_at"),
                    ),
                )
            }
        }
        return if (configs.isEmpty()) null else configs
    }

    fun leggTilIkkeStartedeSinkerForNyeEntiteter(sink: EntitySink, kjoringId: Int) {
        dataSource.connection.use { connection ->

            val updatedAt = Timestamp(System.currentTimeMillis())

            val updateStatement =
                connection.prepareStatement("UPDATE tilbakeføringsstatus SET opprettinger = TRUE, updated_at = ? WHERE kjoringid = ? AND sink = ?")
            updateStatement.setTimestamp(1, updatedAt)
            updateStatement.setInt(2, kjoringId)
            updateStatement.setString(3, sink.id)

            updateStatement.executeUpdate()
        }
    }

    fun leggTilIkkeStartedeSinkerForErstattendeEntiteter(sink: EntitySink, kjoringId: Int) {
        dataSource.connection.use { connection ->

            val updatedAt = Timestamp(System.currentTimeMillis())

            val updateStatement =
                connection.prepareStatement("UPDATE tilbakeføringsstatus SET endringer = TRUE, updated_at = ? WHERE kjoringid = ? AND sink = ?")
            updateStatement.setTimestamp(1, updatedAt)
            updateStatement.setInt(2, kjoringId)
            updateStatement.setString(3, sink.id)

            updateStatement.executeUpdate()
        }
    }

    fun hentIkkeStartedeTilbakeføringerForNyeEntiteter(kjoringId: Int): List<String> {
        val sinkIder = mutableListOf<String>()

        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement(
                    "SELECT sink FROM tilbakeføringsstatus WHERE kjoringid = ? AND (opprettinger = FALSE OR opprettinger IS NULL)",
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
                connection.prepareStatement("SELECT sink FROM tilbakeføringsstatus  WHERE kjoringid = ? AND (endringer = FALSE OR endringer IS NULL)")
            selectStatement.setInt(1, kjoringId)

            val sinkIdResultSet = selectStatement.executeQuery()
            while (sinkIdResultSet.next()) {
                sinkIder.add(sinkIdResultSet.getString("sink"))
            }
        }
        return sinkIder
    }
}
