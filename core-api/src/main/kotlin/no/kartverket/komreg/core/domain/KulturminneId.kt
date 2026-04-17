package no.kartverket.komreg.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * En ident-klasse for å identifisere et spesifikt kulturminne. Den fulle identen vil også inneholde et fiktivt matrikkelnummer.
 * Dette er for å kunne identifisere kulturminner uten matrikkelnummer, slik at de kan fordeles til riktig kommune ved
 * splittinger og grensejusteringer.
 */
@Serializable
@SerialName("KulturminneId")
data class KulturminneId(val id: Long) : Comparable<KulturminneId> {
    override fun compareTo(other: KulturminneId): Int {
        return id.compareTo(other.id)
    }
}