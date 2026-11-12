package no.kartverket.komreg.regulering.api.server.service.reform

import kotlinx.serialization.Serializable

/** Stegene i valideringskjeden. Verdiene er det frontend ser i `ReformFeil.steg`. */
object ReformSteg {
    const val FORM = "form"
    const val FORMAT = "format"
    const val KONFLIKT = "konflikt"
    const val DOMENE = "domene"
    const val DATA = "data"
    const val BRO = "bro"
}

/**
 * En feil i en reform-spec. [sti] er JSON-stien til stedet i specen, [flereSti] andre steder feilen gjelder.
 * Se `docs/reform-format.md`.
 */
@Serializable
data class ReformFeil(
    val sti: String,
    val steg: String,
    val kode: String,
    val melding: String,
    val hint: String? = null,
    val flereSti: List<String> = emptyList(),
)

@Serializable
data class ValideringResultat(val gyldig: Boolean, val feil: List<ReformFeil>) {
    companion object {
        fun ugyldig(feil: Collection<ReformFeil>) = ValideringResultat(gyldig = false, feil = feil.toList())

        val Gyldig = ValideringResultat(gyldig = true, feil = emptyList())
    }
}
