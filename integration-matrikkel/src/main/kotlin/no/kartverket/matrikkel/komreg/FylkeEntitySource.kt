package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.data.RawData
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.getSecretOrString
import no.kartverket.komreg.integration.spi.SimpleEntitySource
import no.kartverket.komreg.integration.spi.SimpleEntitySourceFactory
import java.sql.DriverManager
import java.util.Properties

class FylkeEntitySource(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String,
) : SimpleEntitySource<RawData<Fylke>> {

    override val entityFlow: Flow<RawData<Fylke>>
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
                        st.executeQuery("SELECT f.id, f.fylkesnr, f.fylkesnavn FROM FYLKE f")
                            .use { rs ->
                                while (rs.next()) {
                                    try {
                                        val matrikkelnummer: RawData<Fylke> = Fylke(
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

class FylkeEntitySourceFactory : SimpleEntitySourceFactory<RawData<Fylke>> {
    override fun KrAppBootContext.create(): SimpleEntitySource<RawData<Fylke>> {
        val matrikkelConfig = config.getConfig("integration.matrikkel")
        return FylkeEntitySource(
            matrikkelConfig.getSecretOrString("jdbcUrl"),
            matrikkelConfig.getSecretOrString("user"),
            matrikkelConfig.getSecretOrString("password"),
        )
    }
}
