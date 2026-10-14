package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KjoringContext

interface LifeCycleHandler {
    fun beforeRun(dryRun: Boolean)

    fun afterRun(dryRun: Boolean)
}

interface LifeCycleHandlerFactory {
    fun KjoringContext.create(): LifeCycleHandler
}
