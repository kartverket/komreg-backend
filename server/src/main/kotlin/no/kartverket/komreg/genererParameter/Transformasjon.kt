package regulering.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Transformasjon(
    val type: String,
    val fylkesnummer: FraTil? = null,
    val kommuneløpenummer: FraTil? = null,
    val adressekode: FraTil? = null,
    val adressenummer: FraTil? = null,
    val kretsnummer: FraTil? = null,
    val kretstype: FraTil? = null,
    val teigId: FraTil? = null,
    val gardsnummer: FraTil? = null,
    val bruksnummer: FraTil? = null,
)
