package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.getSecretOrString
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.EntitySourceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.util.Properties

class MatrikkelenhetEntitySource(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) : EntitySource {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override val id: String = "matrikkelenhetEntitySource"
    override val preValidation: Set<() -> Unit> = emptySet()
    override val postValidation: Set<() -> Unit> = emptySet()

    override val entityFlow: Flow<Entity>
        get() = flow {
            val props = Properties()
            props.setProperty("autoCommit", "false")
            props.setProperty("defaultRowPrefetch", "65536")
            props.setProperty("oracle.jdbc.maxCachedBufferSize", "12")
            props.setProperty("user", user)
            props.setProperty("password", password)

            DriverManager.getConnection(jdbcUrl, props)
                .use { conn ->
                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT m.id, m.kommuneid, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr FROM matrikkelenhet m")
                            .use { rs ->
                                while (rs.next()) {
                                    try {
                                        val matrikkelnummer: Matrikkelnummer = Matrikkelnummer(
                                            rs.getLong(2),
                                            rs.getLong(3),
                                            rs.getLong(4),
                                            rs.getLong(5),
                                            rs.getLong(6)
                                        )
                                        emit(
                                            Entity(
                                                id = "matrikkelenhet${matrikkelnummer.hashCode()}",
                                                ident = mapOf<Any, Any?>(
                                                    Fylkesnummer::class to matrikkelnummer.kommunenummer.fylkesnummer,
                                                    Kommunenummer.Lopenummer::class to matrikkelnummer.kommunenummer.lopenummer,
                                                    Matrikkelnummer.Gardsnummer::class to matrikkelnummer.gardsnummer,
                                                    Matrikkelnummer.Bruksnummer::class to matrikkelnummer.gardsnummer,
                                                    Matrikkelnummer.Festenummer::class to matrikkelnummer.festenummer,
                                                    Matrikkelnummer.Seksjonsnummer::class to matrikkelnummer.seksjonsnummer
                                                )
                                            )
                                        )
                                    } catch (ex: Exception) {
                                        logger.error(ex.message)
                                    }
                                }
                            }
                    }
                }
        }
}

class MatrikkelnrEntitySourceFactory : EntitySourceFactory {
    override fun KrAppBootContext.create(): EntitySource {
        val matrikkelConfig = config.getConfig("integration.matrikkel")

        return MatrikkelenhetEntitySource(
            matrikkelConfig.getSecretOrString("jdbcUrl"),
            matrikkelConfig.getSecretOrString("user"),
            matrikkelConfig.getSecretOrString("password")
        )
    }
}
