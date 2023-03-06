package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.data.RawData
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.getSecretOrString
import no.kartverket.komreg.integration.spi.SimpleEntitySource
import no.kartverket.komreg.integration.spi.SimpleEntitySourceFactory
import java.sql.DriverManager
import java.util.Properties

class KommuneEntitySource(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) : SimpleEntitySource<RawData<Kommune>> {

    override val entityFlow: Flow<RawData<Kommune>>
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
                                        val matrikkelnummer: RawData<Kommune> = Kommune(
                                            rs.getLong(2),
                                            rs.getString(3),
                                        )
                                        emit(matrikkelnummer)
                                    } catch (ex: Exception) {
                                        println(ex.message)
                                    }
                                }
                            }
                    }
                }
        }
}

class KommuneEntitySourceFactory : SimpleEntitySourceFactory<RawData<Kommune>> {
    override fun KrAppBootContext.create(): SimpleEntitySource<RawData<Kommune>> {
        val matrikkelConfig = config.getConfig("integration.matrikkel")
        return KommuneEntitySource(
            matrikkelConfig.getSecretOrString("jdbcUrl"),
            matrikkelConfig.getSecretOrString("user"),
            matrikkelConfig.getSecretOrString("password"),
        )
    }
}
