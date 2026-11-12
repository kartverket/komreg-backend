package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.reform.domain.type.MunicipalityNumber
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Postnummeromrade
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.KommuneId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.person.JuridiskPerson
import java.util.concurrent.ConcurrentHashMap

/**
 * Kommune- og fylkesdata fra Matrikkel API. Alt unntatt kommunevåpen lastes ved oppstart. Ved bom (ukjent
 * kommune) lastes alt på nytt én gang, siden kommuner og fylker aldri slettes og dermed bare kan komme til.
 * Det finnes ingen annen invalidering, så data kan være utdatert til neste bom eller omstart.
 *
 * Kommunevåpen hentes første gang en kommune trenger det og beholdes deretter, fordi bare noen få kommuner er
 * med i en reform. Lasting av alle ved oppstart er ikke målt. [findAlleKommuner] må ha alle våpen og laster
 * dermed de som mangler. Kall som ikke trenger våpen (for eksempel datareglene) bør bruke [withoutCoatOfArms].
 *
 * Alle kall er blokkerende og må gjøres fra `Dispatchers.IO` eller en virtuell tråd.
 */
class MatrikkelApiMunicipalityCache(
    private val source: MunicipalityDataSource,
    private val numberingScheme: NumberingScheme,
) : MatrikkelApiMunicipalityNumbers, KommuneService {
    private class Entry(val id: KommuneId, val number: MunicipalityNumber, val municipality: Kommune)

    private class Snapshot(val counties: List<Fylke>, val municipalities: List<Entry>) {
        val byId: Map<Long, Entry> = municipalities.associateBy { it.id.value }
        val byNumber: Map<Kommunenummer, Entry> = municipalities.associateBy { it.municipality.kommunenummer }
    }

    @Volatile
    private var snapshot: Snapshot = load()
    private val coatsOfArms = ConcurrentHashMap<Long, ByteArray>()

    /** Kommunen med kommunenummer [number] eller `null` hvis den ikke finnes, også etter ny lasting. */
    fun findKommune(number: Kommunenummer): Kommune? =
        (snapshot.byNumber[number] ?: reload().byNumber[number])?.withCoatOfArms()

    override fun get(id: MunicipalityId): MunicipalityNumber =
        (snapshot.byId[id.value] ?: reload().byId.getValue(id.value)).number

    override fun findAlleFylker(): List<Fylke> = snapshot.counties

    override fun findAlleKommuner(): List<Kommune> = snapshot.municipalities.map { it.withCoatOfArms() }

    /** Samme data som cachen, men uten kommunevåpen (tomme), slik at ingen våpen må hentes. */
    val withoutCoatOfArms: KommuneService = object : KommuneService {
        override fun findAlleFylker(): List<Fylke> = snapshot.counties
        override fun findAlleKommuner(): List<Kommune> = snapshot.municipalities.map { it.municipality }
    }

    @Synchronized
    private fun reload(): Snapshot = load().also { snapshot = it }

    private fun Entry.withCoatOfArms(): Kommune {
        val coat = coatsOfArms.getOrPut(id.value) { source.fetchCoatOfArms(id) ?: ByteArray(0) }
        return municipality.copy(kommunevapen = coat)
    }

    private fun load(): Snapshot {
        val counties = source.fetchCounties()
        val municipalities = source.fetchMunicipalities()
        val related = source.fetchObjects(
            municipalities.flatMap { listOfNotNull<MatrikkelBubbleId>(it.postadresse?.postnummeromradeId, it.standardRekvirentTinglysingId) }
                .distinct(),
        )
        val postalAreas = related.filterIsInstance<Postnummeromrade>().associateBy { it.id.value }
        val registrars = related.filterIsInstance<JuridiskPerson>().associateBy { it.id.value }

        return Snapshot(
            counties = counties.map { it.toCore() },
            municipalities = municipalities.map {
                Entry(
                    id = KommuneId(it.id.value),
                    number = with(numberingScheme) { MunicipalityNumber(it.kommunenummer) },
                    municipality = it.toCore(
                        postnummeromrade = it.postadresse?.postnummeromradeId?.let { id -> postalAreas[id.value] },
                        standardRekvirent = it.standardRekvirentTinglysingId?.let { id -> registrars[id.value] },
                        kommunevapen = null,
                    ),
                )
            },
        )
    }
}
