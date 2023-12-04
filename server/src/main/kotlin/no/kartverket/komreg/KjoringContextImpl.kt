package no.kartverket.komreg

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import javax.sql.DataSource

class KjoringContextImpl(
    override val kjoringId: Int,
    dataSource: DataSource
) : KjoringContext {
    override val idGenerators: IdGeneratorManager by lazy { IdCache(this, dataSource) }

    override val config: Config = run {
        ConfigFactory.invalidateCaches()
        ConfigFactory.load("properties.conf")
    }
}
