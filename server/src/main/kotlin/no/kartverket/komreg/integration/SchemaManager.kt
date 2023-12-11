package no.kartverket.komreg.integration

import no.kartverket.komreg.repositories.KjoringRepo

class SchemaManager(private val kjoringRepo: KjoringRepo) {

    fun getMottakerUsername(): String {
        return kjoringRepo.hentMottakerSkjema().mottaker.toString() + "_USERNAME"
    }

    fun getMottakerPassword(): String {
        return kjoringRepo.hentMottakerSkjema().mottaker.toString() + "_PASSWORD"
    }
}
