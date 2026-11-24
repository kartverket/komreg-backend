package no.kartverket.komreg.repositories

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.ReformRegulering
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

    fun get(id: String): ReformRegulering? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT navn, ikrafttredelsesdato, regler FROM reform_regulering WHERE id = ?",
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
        }

    /** Oppretter eller erstatter reguleringen [id]. `true` hvis den ble opprettet. */
    fun upsert(id: String, regulering: ReformRegulering): Boolean =
        dataSource.connection.use { connection ->
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
}
