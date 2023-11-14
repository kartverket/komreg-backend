package no.kartverket.komreg.repositories

import no.kartverket.komreg.integration.spi.EntitySink
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class RunConfigRepo(private val dataSource: DataSource) {

    fun createConfigForKjoring(kjoringid: Int, entitySinks: List<EntitySink>) {
        dataSource.connection.use { connection ->

            entitySinks.forEach { sink ->
                val insertStatement =
                    connection.prepareStatement("INSERT into runconfig (id, kjoringId, sink, false, false ) VALUES (?, ?, ?)")
                insertStatement.setString(1, UUID.randomUUID().toString())
                insertStatement.setInt(2, kjoringid)
                insertStatement.setString(3, sink.id)

                insertStatement.executeUpdate()
            }
        }
    }

    fun addNyOpprettingStatusForSink(sink: EntitySink, kjoringid: Int) {
        dataSource.connection.use { connection ->

            val updatedAt = Timestamp(System.currentTimeMillis())

            val updateStatement =
                connection.prepareStatement("UPDATE runconfig SET firstrunsuccess = true, updated_at = ? WHERE kjoringId = ? AND sink = ?")
            updateStatement.setTimestamp(1, updatedAt)
            updateStatement.setInt(2, kjoringid)
            updateStatement.setString(3, sink.id)

            updateStatement.executeUpdate()
        }
    }

    fun addAndreEndringerStatusForSink(sink: EntitySink, kjoringid: Int) {
        dataSource.connection.use { connection ->

            val updatedAt = Timestamp(System.currentTimeMillis())

            val updateStatement =
                connection.prepareStatement("UPDATE runconfig SET secondrunsuccess = true, updated_at = ? WHERE kjoringId = ? AND sink = ?")
            updateStatement.setTimestamp(1, updatedAt)
            updateStatement.setInt(2, kjoringid)
            updateStatement.setString(3, sink.id)

            updateStatement.executeUpdate()
        }
    }
}
