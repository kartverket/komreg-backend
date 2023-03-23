package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.getSecretOrString
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.EntitySourceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.util.Properties

class FylkeEntitySource(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) : EntitySource {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override val id: String = "fylkeEntitySource"
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
                        st.executeQuery("SELECT f.id, f.fylkesnr, f.fylkesnavn FROM fylke f")
                            .use { rs ->
                                while (rs.next()) {
                                    try {
                                        val fylkesnummer = Fylkesnummer(rs.getLong(2))
                                        val fylkesnavn = Fylkesnavn(rs.getString(3))
                                        emit(
                                            Entity(
                                                id = "fylke-${rs.getLong(1)}",
                                                ident = mapOf<Any, Any>(
                                                    Fylkesnummer::class to fylkesnummer,
                                                    Fylkesnavn::class to fylkesnavn,
                                                ),
                                            ),
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

class FylkeEntitySourceFactory : EntitySourceFactory {
    override fun KrAppBootContext.create(): EntitySource {
        val matrikkelConfig = config.getConfig("integration.matrikkel")
        return FylkeEntitySource(
            matrikkelConfig.getSecretOrString("jdbcUrl"),
            matrikkelConfig.getSecretOrString("user"),
            matrikkelConfig.getSecretOrString("password"),
        )
    }
}
