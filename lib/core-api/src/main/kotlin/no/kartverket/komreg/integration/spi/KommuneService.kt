package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.*

/**
 * Denne tjenesten skal implementeres av det systemet som forvalter fylker og kommuner.
 */
interface KommuneService {

    /**
     * Finner alle fylkene.
     */
    fun findAlleFylker(): List<Fylke>

    /**
     * Finner alle kommunene.
     */
    fun findAlleKommuner(): List<Kommune>
}

interface KommuneServiceFactory {
    fun KrAppBootContext.create(): KommuneService
}
