package no.kartverket.komreg.repositories

import no.kartverket.komreg.integration.spi.EntitySink
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

data class SinkConfig(
    val sinkId: String,
    val reguleringId: String,
    val opprettinger: Boolean?,
    val endringer: Boolean?,
    val createdAt: Timestamp,
    val updatedAt: Timestamp?,
)

class TilbakeføringsstatusRepo(private val dataSource: DataSource) {

    fun createConfigForRegulering(reguleringId: String, entitySinks: List<EntitySink>) {
        dataSource.connection.use { connection ->

            entitySinks.forEach { sink ->
                val insertStatement =
                    connection.prepareStatement("INSERT INTO tilbakeføringsstatus (id, reguleringid, sink ) VALUES (?, ?, ?)")
                insertStatement.setString(1, UUID.randomUUID().toString())
                insertStatement.setString(2, reguleringId)
                insertStatement.setString(3, sink.id)

                insertStatement.executeUpdate()
            }
        }
    }

    fun getConfigForKjoring(reguleringId: String): List<SinkConfig>? {
        val configs = mutableListOf<SinkConfig>()
        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement("SELECT * FROM tilbakeføringsstatus WHERE reguleringid = ?")
            selectStatement.setString(1, reguleringId)

            val sinkConfigResultSet = selectStatement.executeQuery()
            while (sinkConfigResultSet.next()) {
                configs.add(
                    SinkConfig(
                        sinkId = sinkConfigResultSet.getString("sink"),
                        reguleringId = sinkConfigResultSet.getString("reguleringId"),
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

    fun addNyOpprettingStatusForSink(sink: EntitySink, reguleringId: String) {
        dataSource.connection.use { connection ->

            val updatedAt = Timestamp(System.currentTimeMillis())

            val updateStatement =
                connection.prepareStatement("UPDATE tilbakeføringsstatus SET opprettinger = TRUE, updated_at = ? WHERE reguleringid = ? AND sink = ?")
            updateStatement.setTimestamp(1, updatedAt)
            updateStatement.setString(2, reguleringId)
            updateStatement.setString(3, sink.id)

            updateStatement.executeUpdate()
        }
    }

    fun addAndreEndringerStatusForSink(sink: EntitySink, reguleringId: String) {
        dataSource.connection.use { connection ->

            val updatedAt = Timestamp(System.currentTimeMillis())

            val updateStatement =
                connection.prepareStatement("UPDATE tilbakeføringsstatus SET endringer = TRUE, updated_at = ? WHERE reguleringid = ? AND sink = ?")
            updateStatement.setTimestamp(1, updatedAt)
            updateStatement.setString(2, reguleringId)
            updateStatement.setString(3, sink.id)

            updateStatement.executeUpdate()
        }
    }

    fun findGjenværendeFørsteSinkerId(reguleringId: String): List<String> {
        val sinkIder = mutableListOf<String>()

        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement(
                    "SELECT sink FROM tilbakeføringsstatus WHERE reguleringid = ? AND (opprettinger = FALSE OR opprettinger IS NULL)",
                )
            selectStatement.setString(1, reguleringId)

            val sinkIdResultSet = selectStatement.executeQuery()
            while (sinkIdResultSet.next()) {
                sinkIder.add(sinkIdResultSet.getString("sink"))
            }
        }
        return sinkIder
    }

    fun findGjenværendeAndreSinkerId(reguleringId: String): List<String> {
        val sinkIder = mutableListOf<String>()
        dataSource.connection.use { connection ->
            val selectStatement =
                connection.prepareStatement("SELECT sink FROM tilbakeføringsstatus  WHERE reguleringid = ? AND (endringer = FALSE OR endringer IS NULL)")
            selectStatement.setString(1, reguleringId)

            val sinkIdResultSet = selectStatement.executeQuery()
            while (sinkIdResultSet.next()) {
                sinkIder.add(sinkIdResultSet.getString("sink"))
            }
        }
        return sinkIder
    }
}
