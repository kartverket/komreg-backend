package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.data.RawData
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.getSecretOrString
import no.kartverket.komreg.integration.spi.SimpleEntitySource
import no.kartverket.komreg.integration.spi.SimpleEntitySourceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.util.Properties

class MatrikkelenhetEntitySource(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) : SimpleEntitySource<RawData<Matrikkelnummer>> {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override val entityFlow: Flow<RawData<Matrikkelnummer>>
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
                                        val matrikkelnummer: RawData<Matrikkelnummer> = Matrikkelnummer(
                                            rs.getLong(2),
                                            rs.getLong(3),
                                            rs.getLong(4),
                                            rs.getLong(5),
                                            rs.getLong(6),
                                        )
                                        emit(matrikkelnummer)
                                    } catch (ex: Exception) {
                                        logger.error(ex.message)
                                    }
                                }
                            }
                    }
                }
        }
}

class MatrikkelnrEntitySourceFactory : SimpleEntitySourceFactory<RawData<Matrikkelnummer>> {
    override fun KrAppBootContext.create(): SimpleEntitySource<RawData<Matrikkelnummer>> {
        val matrikkelConfig = config.getConfig("integration.matrikkel")

        return MatrikkelenhetEntitySource(
            matrikkelConfig.getSecretOrString("jdbcUrl"),
            matrikkelConfig.getSecretOrString("user"),
            matrikkelConfig.getSecretOrString("password"),
        )
    }
}
