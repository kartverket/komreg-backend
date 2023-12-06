import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.serializer
import no.kartverket.komreg.core.domain.IdType

@SerialName("Matrikkeltyper")
enum class Matrikkeltyper : IdType<Long, Matrikkeltyper> {
    Fylke,
    Kommune,
    Matrikkelenhet,
    Teig,
    Teiggrense,
    Teiggrensepunkt,
    Anleggsprojeksjonsflate,
    Anleggsprojeksjonsgrense,
    Anleggsprojeksjonspunkt,
    Forretning,
    Konsesjonsforhold,
    KonsesjonsforholdUtskrift,
    Grunnerverv,
    JordskifteKrevd,
    Klage,
    SamlaFastEiendom,
    AvtaleGrensePunktfeste,
    AvtaleStedbundenRettighet,
    Grunnforurensing,
    Matrikkeladresse,
    Vegadresse,
    Veg,
    Krets,
    Bygning,
    Sefrakminne,
    Kulturminne,
    ;

    override fun compare(o1: Long, o2: Long): Int {
        return o1.compareTo(o2)
    }

    override val valueSerializer = Long.serializer()
}
