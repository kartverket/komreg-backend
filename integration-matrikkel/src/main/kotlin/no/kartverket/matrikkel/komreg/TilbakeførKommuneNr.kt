package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.OldToNewKommune
import no.kartverket.komreg.integration.spi.WriteService
import no.kartverket.komreg.integration.spi.WriteServiceFactory

class TilbakeførKommuneNr(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) : WriteService<OldToNewKommune> {
    override suspend fun write(kommunenummer: Flow<OldToNewKommune>) {
        // Writing to kommune table.
        // Disabled because we started using dev db instead of local db.

        /*val props = Properties()
        props.setProperty("autoCommit", "false")
        props.setProperty("defaultRowPrefetch", "65536")
        props.setProperty("oracle.jdbc.maxCachedBufferSize", "12")
        props.setProperty("user", user)
        props.setProperty("password", password)

        DriverManager.getConnection(jdbcUrl, props)
            .use { conn ->
                conn.createStatement().use { st ->
                    kommunenummer.toList().forEach { kommune ->
                        st.addBatch(
                            "UPDATE KOMMUNE SET KOMMUNENR = ${kommune.newKommune} WHERE KOMMUNENR = ${kommune.oldKommune}",
                        )
                    }
                    st.executeBatch()
                    conn.commit()
                    println("Oppdatert database")
                }
            }*/

        println("Skriver til database (skriving deaktivert)")
    }
}

class KommuneWriteFactory : WriteServiceFactory<OldToNewKommune> {
    override fun KrAppBootContext.create(): WriteService<OldToNewKommune> {
        val matrikkelConfig = config.getConfig("integration.matrikkel")
        return TilbakeførKommuneNr(
            matrikkelConfig.getString("jdbcUrl"),
            matrikkelConfig.getString("user"),
            matrikkelConfig.getString("password"),
        )
    }
}
