package no.kartverket.komreg.repositories

import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.kartverket.komreg.routes.Fylkesdeling
import no.kartverket.komreg.routes.Regulering
import java.sql.Date
import java.sql.DriverManager

class ReguleringRepo(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) {
    fun getAllReguleringer(): List<Regulering> {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
        val statement = connection.prepareStatement("SELECT * FROM regulering")
        val resultSet = statement.executeQuery()

        val reguleringerList = mutableListOf<Regulering>()

        while (resultSet.next()) {
            val reguleringJson = resultSet.getString("regulering")
            val regulering = Json.decodeFromString<Regulering>(reguleringJson)
            reguleringerList.add(regulering)
        }

        statement.close()
        connection.close()

        return reguleringerList
    }

    fun getReguleringById(id: String): Regulering? {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
        val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
        statement.setString(1, id)
        val resultSet = statement.executeQuery()

        val regulering: Regulering? = if (resultSet.next()) {
            val reguleringJson = resultSet.getString("regulering")
            Json.decodeFromString<Regulering>(reguleringJson)
        } else {
            null
        }

        statement.close()
        connection.close()

        return regulering
    }

    fun insertRegulering(regulering: Regulering): Boolean {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
        val statement = connection.prepareStatement(
            "INSERT INTO regulering (id, regulering, ikrafttredelsesdato, opprettet, endret, opprettetav) VALUES (?, ?::jsonb, ?, now(), now(), ?)",
        )

        statement.setString(1, regulering.id)
        statement.setString(2, Json.encodeToString(Regulering.serializer(), regulering))
        statement.setDate(3, Date.valueOf(regulering.dato.toJavaLocalDate()))
        statement.setString(4, "system")

        val affectedRows = statement.executeUpdate()

        statement.close()
        connection.close()

        return affectedRows > 0
    }

    fun updateRegulering(regulering: Regulering): Boolean {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)

        val checkStatement = connection.prepareStatement("SELECT count(id) FROM regulering WHERE id = ?")
        checkStatement.setString(1, regulering.id)
        val resultSet = checkStatement.executeQuery()
        resultSet.next()
        val count = resultSet.getInt(1)
        checkStatement.close()

        if (count == 0) {
            connection.close()
            return false
        }

        val updateStatement = connection.prepareStatement(
            "UPDATE regulering SET regulering = ?::jsonb, ikrafttredelsesdato = ?, endret = now(), opprettetav = ? WHERE ID = ?",
        )
        updateStatement.setString(1, Json.encodeToString(Regulering.serializer(), regulering))
        updateStatement.setDate(2, Date.valueOf(regulering.dato.toJavaLocalDate()))
        updateStatement.setString(3, "system")
        updateStatement.setString(4, regulering.id)

        val affectedRows = updateStatement.executeUpdate()

        updateStatement.close()
        connection.close()

        return affectedRows > 0
    }

    fun deleteReguleringById(regId: String): Boolean {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)

        val checkStatement = connection.prepareStatement("SELECT count(id) FROM regulering WHERE id = ?")
        checkStatement.setString(1, regId)
        val resultSet = checkStatement.executeQuery()
        resultSet.next()
        val count = resultSet.getInt(1)
        checkStatement.close()

        if (count == 0) {
            connection.close()
            return false
        }

        val deleteStatement = connection.prepareStatement("DELETE FROM regulering WHERE id = ?")
        deleteStatement.setString(1, regId)
        val affectedRows = deleteStatement.executeUpdate()

        deleteStatement.close()
        connection.close()

        return affectedRows > 0
    }

    fun getEndringFromRegulering(regId: String, endrId: String): Fylkesdeling? {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)

        val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
        statement.setString(1, regId)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            val reguleringJson = resultSet.getString("regulering")
            val regulering = Json.decodeFromString<Regulering>(reguleringJson)

            statement.close()
            connection.close()

            return regulering.endringer.find { it.id == endrId }
        }

        statement.close()
        connection.close()
        return null
    }

    fun getAllEndringerFromRegulering(regId: String): List<Fylkesdeling>? {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)

        val statement = connection.prepareStatement("SELECT regulering FROM regulering WHERE id = ?")
        statement.setString(1, regId)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            val reguleringJson = resultSet.getString("regulering")
            val regulering = Json.decodeFromString<Regulering>(reguleringJson)

            statement.close()
            connection.close()

            return regulering.endringer
        }

        statement.close()
        connection.close()
        return null
    }

    fun addEndringToRegulering(regId: String, endring: Fylkesdeling): Boolean {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)

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
            connection.close()
            return true
        }

        statement.close()
        connection.close()
        return false
    }

    fun updateEndringOfRegulering(regId: String, endringId: String, updatedEndring: Fylkesdeling): Boolean {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)

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
                connection.close()
                return true
            }
        }

        statement.close()
        connection.close()
        return false
    }

    fun deleteEndringFromRegulering(regId: String, endringId: String): Boolean {
        val connection = DriverManager.getConnection(jdbcUrl, user, password)
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
                connection.close()
                return true
            }
        }

        statement.close()
        connection.close()
        return false
    }
}
