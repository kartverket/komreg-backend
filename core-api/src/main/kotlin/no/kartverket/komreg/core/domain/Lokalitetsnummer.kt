package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * En ident-klasse for å identifisere et spesifikt kulturminne. Den fulle identen vil også inneholde et fiktivt matrikkelnummer.
 * Dette er for å kunne identifisere kulturminner uten matrikkelnummer, slik at de kan fordeles til riktig kommune ved
 * splittinger og grensejusteringer.
 */
@Serializable
@SerialName("Lokalitetsnummer")
data class Lokalitetsnummer(val id: Long) : Comparable<Lokalitetsnummer> {
    override fun compareTo(other: Lokalitetsnummer): Int {
        return id.compareTo(other.id)
    }
}