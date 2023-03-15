package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.getSecretOrString
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.EntitySourceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.util.Properties

class KommuneEntitySource(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) : EntitySource {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override val id: String = "kommuneEntitySource"
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
                        st.executeQuery("SELECT m.id, m.kommunenr, m.kommunenavn FROM KOMMUNE m")
                            .use { rs ->
                                while (rs.next()) {
                                    try {
                                        val kommune: Kommune = Kommune(
                                            rs.getLong(2),
                                            rs.getString(3)
                                        )
                                        emit(
                                            Entity(
                                                id = "kommune${kommune.hashCode()}",
                                                ident = mapOf<Any, Any?>(
                                                    Fylkesnummer::class to kommune.kommunenummer.fylkesnummer,
                                                    Kommunenummer.Lopenummer::class to kommune.kommunenummer.lopenummer,
                                                    Kommunenavn::class to kommune.kommunenavn
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

class KommuneEntitySourceFactory : EntitySourceFactory {
    override fun KrAppBootContext.create(): EntitySource {
        val matrikkelConfig = config.getConfig("integration.matrikkel")
        return KommuneEntitySource(
            matrikkelConfig.getSecretOrString("jdbcUrl"),
            matrikkelConfig.getSecretOrString("user"),
            matrikkelConfig.getSecretOrString("password")
        )
    }
}
