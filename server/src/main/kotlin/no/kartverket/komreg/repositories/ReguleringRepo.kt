package no.kartverket.komreg.repositories

import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.kartverket.komreg.routes.EndringDTO
import no.kartverket.komreg.routes.KommuneDTO
import no.kartverket.komreg.routes.Regulering
import java.sql.Date
import javax.sql.DataSource

class ReguleringRepo(
    private val dataSource: DataSource,
) {
    fun getAllReguleringer(): List<Regulering> {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement("SELECT regulering FROM regulering")
            val resultSet = statement.executeQuery()

            val reguleringerList = mutableListOf<Regulering>()

            while (resultSet.next()) {
                val reguleringJson = resultSet.getString(1)
                val regulering = Json.decodeFromString<Regulering>(reguleringJson)
                reguleringerList.add(regulering)
            }

            statement.close()

            return reguleringerList
        }
    }

    fun getReguleringById(id: String): Regulering? {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
            statement.setString(1, id)
            val resultSet = statement.executeQuery()

            val regulering: Regulering? = if (resultSet.next()) {
                val reguleringJson = resultSet.getString(1)
                Json.decodeFromString<Regulering>(reguleringJson)
            } else {
                null
            }

            statement.close()

            return regulering
        }
    }

    fun insertRegulering(regulering: Regulering): Boolean {
        dataSource.connection.use { connection ->
            val currentTime = java.sql.Timestamp(System.currentTimeMillis())
            val statement = connection.prepareStatement(
                "INSERT INTO regulering (id, regulering, ikrafttredelsesdato, opprettet, endret, opprettetav) VALUES (?, ?::jsonb, ?, ?, ?, ?)",
            )

            statement.setString(1, regulering.id)
            statement.setString(2, Json.encodeToString(regulering))
            statement.setDate(3, Date.valueOf(regulering.dato.toJavaLocalDate()))
            statement.setTimestamp(4, currentTime)
            statement.setTimestamp(5, currentTime)
            statement.setString(6, "system")

            val affectedRows = statement.executeUpdate()

            statement.close()

            return affectedRows > 0
        }
    }

    fun updateRegulering(regulering: Regulering): Boolean {
        dataSource.connection.use { connection ->

            val checkStatement = connection.prepareStatement("SELECT count(id) FROM regulering WHERE id = ?")
            checkStatement.setString(1, regulering.id)
            val resultSet = checkStatement.executeQuery()
            resultSet.next()
            val count = resultSet.getInt(1)
            checkStatement.close()

            if (count == 0) {
                return false
            }

            val updateStatement = connection.prepareStatement(
                "UPDATE regulering SET regulering = ?::jsonb, ikrafttredelsesdato = ?, endret = now(), opprettetav = ? WHERE ID = ?",
            )
            updateStatement.setString(1, Json.encodeToString(regulering))
            updateStatement.setDate(2, Date.valueOf(regulering.dato.toJavaLocalDate()))
            updateStatement.setString(3, "system")
            updateStatement.setString(4, regulering.id)

            val affectedRows = updateStatement.executeUpdate()

            updateStatement.close()

            return affectedRows > 0
        }
    }

    fun deleteReguleringById(regId: String): Boolean {
        dataSource.connection.use { connection ->

            val checkStatement = connection.prepareStatement("SELECT count(id) FROM regulering WHERE id = ?")
            checkStatement.setString(1, regId)
            val resultSet = checkStatement.executeQuery()
            resultSet.next()
            val count = resultSet.getInt(1)
            checkStatement.close()

            if (count == 0) {
                return false
            }


            val deleteStatement = connection.prepareStatement("DELETE FROM regulering WHERE id = ?")
            deleteStatement.setString(1, regId)
            val affectedRows = deleteStatement.executeUpdate()

            deleteStatement.close()

            return affectedRows > 0
        }
    }

    fun getEndringFromRegulering(regId: String, endrId: String): EndringDTO? {
        dataSource.connection.use { connection ->

            val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
            statement.setString(1, regId)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                val reguleringJson = resultSet.getString(1)
                val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                statement.close()
                connection.close()

                return regulering.endringer.find { it.id == endrId }
            }

            statement.close()
            return null
        }
    }

    fun getAllEndringerFromRegulering(regId: String): List<EndringDTO>? {
        dataSource.connection.use { connection ->

            val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
            statement.setString(1, regId)
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                val reguleringJson = resultSet.getString(1)
                val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                statement.close()

                return regulering.endringer
            }

            statement.close()
            return null
        }
    }

    fun addEndringToRegulering(regId: String, endring: EndringDTO): Boolean {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
            statement.setString(1, regId)

            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val reguleringJson = resultSet.getString("regulering")
                val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                val updatedEndringer = regulering.endringer + endring
                val updatedRegulering = regulering.copy(endringer = updatedEndringer)

                val updateStatement =
                    connection.prepareStatement("UPDATE regulering SET regulering = ?::jsonb WHERE ID = ?")
                updateStatement.setString(1, Json.encodeToString(updatedRegulering))
                updateStatement.setString(2, regId)
                updateStatement.executeUpdate()

                statement.close()
                return true
            }

            statement.close()
            return false
        }
    }

    fun updateEndringOfRegulering(regId: String, endringId: String, updatedEndring: EndringDTO): Boolean {
        dataSource.connection.use { connection ->

            val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
            statement.setString(1, regId)

            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val reguleringJson = resultSet.getString("regulering")
                val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                val endringIndex = regulering.endringer.indexOfFirst { it.id == endringId }
                if (endringIndex != -1) {
                    val updatedEndringer = regulering.endringer.toMutableList()
                    updatedEndringer[endringIndex] = updatedEndring
                    val updatedRegulering = regulering.copy(endringer = updatedEndringer)

                    val updateStatement =
                        connection.prepareStatement("UPDATE regulering SET regulering = ?::jsonb WHERE ID = ?")
                    updateStatement.setString(1, Json.encodeToString(updatedRegulering))
                    updateStatement.setString(2, regId)
                    updateStatement.executeUpdate()

                    statement.close()
                    return true
                }
            }

            statement.close()
            return false
        }
    }

    fun deleteEndringFromRegulering(regId: String, endringId: String): Boolean {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
            statement.setString(1, regId)

            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                val reguleringJson = resultSet.getString("regulering")
                val regulering = Json.decodeFromString<Regulering>(reguleringJson)

                val endringIndex = regulering.endringer.indexOfFirst { it.id == endringId }
                if (endringIndex != -1) {
                    val updatedEndringer = regulering.endringer.toMutableList()
                    updatedEndringer.removeAt(endringIndex)
                    val updatedRegulering = regulering.copy(endringer = updatedEndringer)

                    val updateStatement =
                        connection.prepareStatement("UPDATE regulering SET regulering = ?::jsonb WHERE ID = ?")
                    updateStatement.setString(1, Json.encodeToString(updatedRegulering))
                    updateStatement.setString(2, regId)
                    updateStatement.executeUpdate()

                    statement.close()
                    return true
                }
            }

            statement.close()
            return false
        }
    }

    fun getNyKommuneFromEndring(
        reguleringsId: String,
        endringId: String,
        fylkesnummer: String,
        kommunelopenummer: String
    ): KommuneDTO? {
        dataSource.connection.use { connection ->
            val prepareStatement = connection.prepareStatement(
                """
                SELECT nyekommuner AS nyekommuner
                FROM regulering r, jsonb_array_elements(r.regulering->'endringer') AS endringer,
                    jsonb_array_elements(endringer->'nyeKommuner') AS nyekommuner
                WHERE (nyekommuner->>'fylkesnummer' = ?)
                    AND (nyekommuner->>'kommunenummer' = ?) 
                    AND r.id = ? 
                    AND endringer->>'id' =  ?
            """
            )
            prepareStatement.setString(1, fylkesnummer)
            prepareStatement.setString(2, kommunelopenummer)
            prepareStatement.setString(3, reguleringsId)
            prepareStatement.setString(4, endringId)

            val resultSet = prepareStatement.executeQuery()

            if (resultSet.next()) {
                val nyKommuneJson = resultSet.getString("nyekommuner")
                val nyKommune = Json.decodeFromString<KommuneDTO>(nyKommuneJson)
                return nyKommune
            }

            return null
        }
    }
}
