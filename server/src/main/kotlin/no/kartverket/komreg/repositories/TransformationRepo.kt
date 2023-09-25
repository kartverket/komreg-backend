package no.kartverket.komreg.repositories

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import no.kartverket.komreg.PolymorphicEnumSerializer
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.RegisterSerialization
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.logger
import no.statkart.matrikkel.komreg.model.*
import java.sql.DriverManager
import java.sql.SQLException
import java.util.ServiceLoader

class TransformationRepo(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
    private val jsonSerializer: Json,
) {
    fun writeTransformationsToDatabase(kjoringId: Int, transformResultList: List<Transformation>) {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
        try {
            val statement = connection.prepareStatement(
                "INSERT INTO transformasjon (transformasjonsid, kjoring, transformasjon, tid) VALUES (?::jsonb, ?, ?::jsonb, now())",
            )

            logger.info("number of transformations: ${transformResultList.size}")
            for (transformation in transformResultList) {
                statement.setString(1, jsonSerializer.encodeToString(Id.serializer(), transformation.id))
                statement.setInt(2, kjoringId)
                statement.setString(3, jsonSerializer.encodeToString(Transformation.serializer(), transformation))
                statement.addBatch()
            }

            statement.executeBatch()
            statement.close()
        } catch (e: SQLException) {
            logger.error("Error writing to transformasjon table: ${e.message}")
        } finally {
            connection.close()
        }
    }
}
