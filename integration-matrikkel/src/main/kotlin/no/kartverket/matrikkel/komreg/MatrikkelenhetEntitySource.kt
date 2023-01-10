package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.Product
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.product
import no.kartverket.komreg.integration.spi.EntitySource
import no.kartverket.komreg.integration.spi.EntitySourceFactory
import no.kartverket.komreg.integration.spi.SourceEntityContext
import java.sql.DriverManager
import java.util.Properties

class MatrikkelenhetEntitySource(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String
) : EntitySource<Product.Just<Matrikkelnummer>> {

    class Factory : EntitySourceFactory {
        override fun KrAppBootContext.create(): EntitySource<Product<*>> {
            val matrikkelConfig = config.getConfig("integration.matrikkel")
            return MatrikkelenhetEntitySource(
                matrikkelConfig.getString("jdbcUrl"),
                matrikkelConfig.getString("user"),
                matrikkelConfig.getString("password")
            )
        }

    }

    override val entityFlow: Flow<SourceEntityContext<Product.Just<Matrikkelnummer>>>
        get() = flow {
            val props = Properties()
            props.setProperty("autoCommit","false")
            props.setProperty("defaultRowPrefetch","65536")
            props.setProperty("oracle.jdbc.maxCachedBufferSize","12")
            props.setProperty("user", user)
            props.setProperty("password", password)

            DriverManager.getConnection(jdbcUrl, props)
                .use { conn ->

                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT m.id, m.kommuneid, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr FROM matrikkelenhet m")
                            .use { rs ->
                                while (rs.next()) {
                                    val matrikkelnummer = Matrikkelnummer(
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getLong(6)
                                    )
                                    emit(SourceEntityContext(rs.getLong(1), matrikkelnummer.map { it.product }))
                                }
                            }
                    }
                }
        }
}