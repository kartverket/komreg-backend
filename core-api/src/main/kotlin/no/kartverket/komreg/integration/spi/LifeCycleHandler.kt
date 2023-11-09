package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext

interface LifeCycleHandler {
    fun beforeRun(dryRun: Boolean)

    fun afterRun(dryRun: Boolean)
}

interface LifeCycleHandlerFactory {
    fun KrAppBootContext.create(): LifeCycleHandler
}
