package no.kartverket.komreg.repositories

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.ReformRegulering
import java.sql.Connection
import java.sql.Date
import javax.sql.DataSource

/**
 * Lagring av [ReformRegulering] i `reform_regulering`. [json] må kunne serialisere identer og payloads
 * (`jsonSerializer()`).
 */
class ReformReguleringRepo(
    private val dataSource: DataSource,
    private val json: Json,
) {
    private val reglerSerializer = ListSerializer(ReformOp.serializer())

    fun get(id: String): ReformRegulering? = dataSource.connection.use { get(it, id, forUpdate = false) }

    /**
     * Henter reguleringen [id] og låser raden ut transaksjonen på [connection], slik at reglene ikke kan
     * endres av [upsert] før transaksjonen er ferdig.
     */
    fun getForUpdate(connection: Connection, id: String): ReformRegulering? = get(connection, id, forUpdate = true)

    /** Låser raden for [id] ut transaksjonen på [connection], hvis den finnes. */
    fun lock(connection: Connection, id: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { statement ->
            statement.setString(1, "reform_regulering:$id")
            statement.executeQuery().close()
        }
        connection.prepareStatement("SELECT 1 FROM reform_regulering WHERE id = ? FOR UPDATE").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().close()
        }
    }

    private fun get(connection: Connection, id: String, forUpdate: Boolean): ReformRegulering? =
        connection.prepareStatement(
            "SELECT navn, ikrafttredelsesdato, regler FROM reform_regulering WHERE id = ?" +
                if (forUpdate) " FOR UPDATE" else "",
        ).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                ReformRegulering(
                    navn = result.getString("navn"),
                    ikrafttredelsesdato = result.getDate("ikrafttredelsesdato").toLocalDate().toKotlinLocalDate(),
                    regler = json.decodeFromString(reglerSerializer, result.getString("regler")),
                )
            }
        }

    /** Oppretter eller erstatter reguleringen [id]. `true` hvis den ble opprettet. */
    fun upsert(id: String, regulering: ReformRegulering): Boolean =
        dataSource.connection.use { upsert(it, id, regulering) }

    fun upsert(connection: Connection, id: String, regulering: ReformRegulering): Boolean =
        connection.prepareStatement(
            """
            INSERT INTO reform_regulering (id, navn, ikrafttredelsesdato, regler, opprettet, endret, opprettetav)
            VALUES (?, ?, ?, ?::jsonb, now(), now(), ?)
            ON CONFLICT (id) DO UPDATE SET
                navn = EXCLUDED.navn,
                ikrafttredelsesdato = EXCLUDED.ikrafttredelsesdato,
                regler = EXCLUDED.regler,
                endret = now(),
                opprettetav = EXCLUDED.opprettetav
            RETURNING (xmax = 0) AS opprettet
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, regulering.navn)
            statement.setDate(3, Date.valueOf(regulering.ikrafttredelsesdato.toJavaLocalDate()))
            statement.setString(4, json.encodeToString(reglerSerializer, regulering.regler))
            statement.setString(5, "system")
            statement.executeQuery().use { result ->
                result.next()
                result.getBoolean("opprettet")
            }
        }
}
