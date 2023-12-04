package no.kartverket.komreg

import no.kartverket.komreg.core.KjoringContext
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.IdGeneratorManager
import javax.sql.DataSource

class KjoringContextImpl(
    override val kjoringId: Int,
    dataSource: DataSource
) : KjoringContext, KrAppBootContext by KrAppBootContextImpl {
    override val idGenerators: IdGeneratorManager by lazy {
        IdCache(this, dataSource)
    }
}
