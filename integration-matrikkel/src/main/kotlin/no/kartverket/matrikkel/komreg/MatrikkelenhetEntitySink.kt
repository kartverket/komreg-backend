package no.kartverket.matrikkel.komreg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.spi.EntitySink
import no.kartverket.komreg.integration.spi.EntitySinkFactory
import no.kartverket.komreg.integration.spi.Transformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MatrikkelenhetEntitySink : EntitySink {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override val id: String = "test-sink"

    override suspend fun consumeTransformations(flow: Flow<Transformation>) {
        logger.info("Nom nom")
        flow.onEach {
            logger.info("Tilbakefører: $it")
        }.toList()
        logger.info("Done nom nom-ing")
    }

    override val postValidation: Set<() -> Unit>
        get() = TODO("Not yet implemented")
    override val preValidation: Set<() -> Unit>
        get() = TODO("Not yet implemented")
}

class MatrikkelenhetEntitySinkFactory : EntitySinkFactory {
    override fun KrAppBootContext.create(): EntitySink {
        return MatrikkelenhetEntitySink()
    }
}
