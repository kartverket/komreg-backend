package no.kartverket.komreg.core.domain

import kotlinx.serialization.Serializable

/**
 * En ident-klasse for å identifisere en spesifik teig. Den fulle identen vil også inneholde et fiktivt matrikkelnummer.
 * Dette er for å kunne identifisere teiger uten matrikkelnummer, slik at de kan fordeles til riktig kommune ved
 * splittinger og grensejusteringer.
 */
@Serializable
data class TeigId(val id: Long) : Comparable<TeigId> {
    override fun compareTo(other: TeigId): Int {
        return id.compareTo(other.id)
    }
}
