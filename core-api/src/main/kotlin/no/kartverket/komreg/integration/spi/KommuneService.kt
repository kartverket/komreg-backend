package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.*

/**
 * Denne tjenesten skal implementeres av det systemet som forvalter fylker og kommuner.
 */
interface KommuneService {
    /**
     * Bestemmer hvilken id som skal brukes for et nytt fylke med gitt fylkesnummer.
     */
    fun idForFylke(fylkesnummer: Fylkesnummer): Id<*>

    /**
     * Bestemmer hvilken id som skal brukes for en ny kommune med gitt kommunenummer.
     */
    fun idForKommune(kommunenummer: Kommunenummer): Id<*>

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
