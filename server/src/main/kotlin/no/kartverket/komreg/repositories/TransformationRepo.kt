package no.kartverket.komreg.repositories

import kotlinx.serialization.json.Json
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.logger
import javax.sql.DataSource

class TransformationRepo(
    private val dataSource: DataSource,
    private val jsonSerializer: Json,
) {
    fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO transformasjon (transformasjonsid, kjoring, transformasjon, tid) VALUES (?::jsonb, ?, ?::jsonb, now())",
            ).use { statement ->
                logger.info("number of transformations: ${transformResultList.size}")
                for (transformation in transformResultList) {
                    statement.setString(1, jsonSerializer.encodeToString(Id.serializer(), transformation.id))
                    statement.setInt(2, kjoringId)
                    statement.setString(3, jsonSerializer.encodeToString(Transformation.serializer(), transformation))
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }
}
