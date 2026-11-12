package no.kartverket.komreg.routes

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.core.domain.verdi
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.toReformOps
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.reflect.KClass

private val log = LoggerFactory.getLogger("no.kartverket.komreg.routes.ReformClaimsTilRegulering")

/** Grunnen til at [ReformClaims] ikke lot seg uttrykke som en [Regulering] eller [EndringDTO]. */
sealed interface Umappbar {
    val melding: String

    /** Reglene har ingen ekvivalent i DTO-en, eller en kombinasjon av regler kan ikke uttrykkes. */
    data class IngenDtoEkvivalent(val ops: List<ReformOp>, val grunn: String) : Umappbar {
        override val melding get() = "$grunn: $ops"
    }

    /** Et mål som ikke opprettes av reglene, finnes ikke i [KommuneService]. */
    data class FinnesIkke(val nøkkel: Ident.NonEmpty) : Umappbar {
        override val melding get() = "$nøkkel opprettes ikke av reglene, og finnes ikke i KommuneService"
    }

    /** Payloaden til en ny fylke eller kommune mangler, har feil type eller kan ikke gjøres om til en DTO. */
    data class UgyldigPayload(val mål: Ident.NonEmpty, val payload: Payload?, val grunn: String) : Umappbar {
        override val melding get() = "Ugyldig payload for $mål ($grunn): $payload"
    }

    /** Regulering-DTO-en ville gitt andre ReformOp-regler enn de opprinnelige. */
    data class UlikTransformasjon(
        val mangler: Set<ReformOp> = emptySet(),
        val ekstra: Set<ReformOp> = emptySet(),
        val årsak: Throwable? = null,
    ) : Umappbar {
        override val melding
            get() = "Reguleringen gir ikke samme ReformOp-regler. Mangler: $mangler. Ekstra: $ekstra." +
                (årsak?.let { " Årsak: $it" } ?: "")
    }
}

class UmappbarReformException(val feil: NonEmptyList<Umappbar>) :
    IllegalArgumentException(feil.joinToString("\n") { it.melding })

/**
 * Lager en [Regulering] som kan gjenskape disse [ReformOp]-reglene.
 *
 * `toReguleringsinput().toReformOps()` på resultatet må gi samme mengde regler som
 * [ReformClaims.declarations], med like payloads. Rekkefølgen er ikke garantert. Dette er en strukturell
 * rundturssjekk, ikke en garanti for at den eksisterende legacy-transformeren gir samme resultat eller ID-er
 * som en fremtidig kjøring av [ReformOp]. Likheten sjekkes før resultatet returneres.
 *
 * En regulering har én ikrafttredelsesdato som gjelder alle nye kommuner. Nye kommuner med en annen
 * [Kommunedata.ikrafttredelsesdato] får [dato], og avviket logges som en advarsel. Ellers gir resultatet
 * samme transformasjon som reglene.
 *
 * Bare det transformasjonen leser, bevares:
 * - Alle transformasjoner legges i én [EndringDTO] (se [toEndringDTO]).
 * - Navn på nye kommuner har alltid store bokstaver, også for kommuner som finnes fra før. [Kommunedata.navn]
 *   har ikke den opprinnelige skrivemåten. `NK`-linjene i stedsnavn-parameterfilen
 *   (`GET /stedsnavn/parameter/{kjoring}`, tabulatorseparert med en kode på to bokstaver først på hver linje:
 *   `NK<TAB>kommunenummer<TAB>navn`, én per ny kommune) skrives derfor med store bokstaver. De andre kodene
 *   (`DT` ikrafttredelsesdato, `NF` nytt fylke, `UK` utgående kommune til etterfølger, `UF` utgående fylke og
 *   `AN` plassholder for SSR-JSON) påvirkes ikke av denne normaliseringen. Fylkesnavn beholder skrivemåten.
 * - Felt som ikke inngår i [Kommunedata] (poststed, navn på standardrekvirent og `gyldigTilDato`) blir tomme
 *   for nye kommuner.
 *
 * @param id id på reguleringen og endringen.
 * @param navn navn på reguleringen og endringen.
 * @param dato ikrafttredelsesdato for reguleringen og alle nye kommuner.
 * @param kommuneService oppslag av kilder og av mål som finnes fra før.
 * @return reguleringen, eller alle grunnene til at reglene ikke kan uttrykkes som en regulering.
 */
suspend fun ReformClaims.toReguleringDTO(
    id: String,
    navn: String,
    dato: LocalDate,
    kommuneService: KommuneService,
): Either<NonEmptyList<Umappbar>, Regulering> {
    val avvikendeDato = declarations.mapNotNull { op ->
        op.kommunedata()?.takeIf { it.ikrafttredelsesdato != dato }?.let { op.nøkkel to it.ikrafttredelsesdato }
    }
    if (avvikendeDato.isNotEmpty()) {
        log.warn(
            "Regulering $id: ikrafttredelsesdato settes til $dato for ${avvikendeDato.size} nye kommuner: " +
                avvikendeDato.joinToString { (mål, gammel) -> "$mål ($gammel)" },
        )
    }
    val forventet = declarations.map { it.medIkrafttredelsesdato(dato) }.toSet()

    return toEndringDTO(id, navn, kommuneService).flatMap { endring ->
        val regulering = Regulering(id, navn, dato, listOf(endring))
        sjekkEkvivalens(regulering, forventet).map { regulering }
    }
}

/** Som [toReguleringDTO], men kaster [UmappbarReformException] med alle feilene hvis reglene ikke kan mappes. */
suspend fun ReformClaims.toReguleringDTOOrThrow(
    id: String,
    navn: String,
    dato: LocalDate,
    kommuneService: KommuneService,
): Regulering = toReguleringDTO(id, navn, dato, kommuneService).getOrElse { throw UmappbarReformException(it) }

/**
 * Lager en [EndringDTO] som kan representere reglene alene i en regulering. Resultatet sjekkes ikke ved
 * rundtur; bruk [toReguleringDTO] for det.
 *
 * Krav til reguleringen endringen legges i:
 * - `dato` må være lik [Kommunedata.ikrafttredelsesdato] for de nye kommunene, hvis det finnes noen.
 * - Ingen andre endringer kan ha de samme fylke- eller kommunemålene. Da tolkes målene annerledes
 *   (for eksempel blir et mål avledet fra én kilde til et mål opprettet fra flere kilder).
 *
 * Bare det transformasjonen leser, bevares:
 * - `type` er `"fylke"` hvis endringen har fylketransformasjoner, ellers `"kommune"`.
 * - `utgåendeFylker` og `utgåendeKommuner` fylles ut for kildene som finnes i [kommuneService].
 * - `nyeFylker` og `nyeKommuner` inneholder alle fylke- og kommunemål, også dem som finnes fra før
 *   (sammenslåing inn i en eksisterende kommune). Payload hentes fra `Create`/`Derive`. Mål uten payload
 *   hentes fra [kommuneService].
 * - Navn på nye kommuner har alltid store bokstaver, også for kommuner som finnes fra før. Se [toReguleringDTO].
 * - Felt som ikke inngår i [Kommunedata] (poststed, navn på standardrekvirent og `gyldigTilDato`) blir tomme
 *   for nye kommuner.
 * - Når flere kilder slås sammen til en ny fylke eller kommune, får én kilde `sammenslaa = false` og resten `true`,
 *   slik at målet opprettes én gang.
 * - Ved kommunedeling står etterfølgeren først i `kommuneløpenummer.til`.
 *
 * @param id id på endringen.
 * @param navn navn på endringen.
 * @param kommuneService oppslag av kilder og av mål som finnes fra før.
 * @return endringen, eller alle grunnene til at reglene ikke kan uttrykkes som en endring.
 */
suspend fun ReformClaims.toEndringDTO(
    id: String,
    navn: String,
    kommuneService: KommuneService,
): Either<NonEmptyList<Umappbar>, EndringDTO> {
    val trengerKommuneService = declarations.any { it.nøkkel.form() in setOf(Form.FYLKE, Form.KOMMUNE) }
    val fylker = if (trengerKommuneService) {
        withContext(Dispatchers.IO) { kommuneService.findAlleFylker() }.associateBy { it.fylkesnummer }
    } else {
        emptyMap()
    }
    val kommuner = if (trengerKommuneService) {
        withContext(Dispatchers.IO) { kommuneService.findAlleKommuner() }.associateBy { it.kommunenummer }
    } else {
        emptyMap()
    }
    return EndringMapper(this, fylker, kommuner).endring(id, navn)
}

/** Sjekker at [regulering] kan gjenskape nøyaktig reglene i [forventet], uavhengig av rekkefølge. */
private suspend fun sjekkEkvivalens(regulering: Regulering, forventet: Set<ReformOp>): Either<NonEmptyList<Umappbar>, Unit> {
    val tilbake = Either.catch { regulering.toReguleringsinput().toReformOps().toSet() }
        .getOrElse { return nonEmptyListOf<Umappbar>(Umappbar.UlikTransformasjon(årsak = it)).left() }
    return if (tilbake == forventet) {
        Unit.right()
    } else {
        nonEmptyListOf<Umappbar>(Umappbar.UlikTransformasjon(forventet - tilbake, tilbake - forventet)).left()
    }
}

private fun ReformOp.kommunedata(): Kommunedata? = when (this) {
    is ReformOp.Create -> payload as? Kommunedata
    is ReformOp.Derive -> payload as? Kommunedata
    else -> null
}

private fun ReformOp.medIkrafttredelsesdato(dato: LocalDate): ReformOp = when (this) {
    is ReformOp.Create ->
        (payload as? Kommunedata)?.let { copy(payload = it.copy(ikrafttredelsesdato = dato)) } ?: this
    is ReformOp.Derive ->
        (payload as? Kommunedata)?.let { copy(payload = it.copy(ikrafttredelsesdato = dato)) } ?: this
    else -> this
}

/** Identtypene som kan uttrykkes i en regulering, med komponentene i rekkefølge. */
private enum class Form(val komponenter: List<KClass<*>>) {
    FYLKE(listOf(Fylkesnummer::class)),
    KOMMUNE(listOf(Fylkesnummer::class, Kommunenummer.Lopenummer::class)),
    VEG(listOf(Fylkesnummer::class, Kommunenummer.Lopenummer::class, Adressekode::class)),
    VEGADRESSE(
        listOf(Fylkesnummer::class, Kommunenummer.Lopenummer::class, Adressekode::class, Adressenummernummer::class),
    ),
    KRETS(listOf(Fylkesnummer::class, Kommunenummer.Lopenummer::class, Kretstype::class, Kretsnummer::class)),
    SERIE(listOf(Fylkesnummer::class, Kommunenummer.Lopenummer::class, Matrikkelnummer.Gardsnummer::class)),
    GRUNNEIENDOM(
        listOf(
            Fylkesnummer::class,
            Kommunenummer.Lopenummer::class,
            Matrikkelnummer.Gardsnummer::class,
            Matrikkelnummer.Bruksnummer::class,
        ),
    ),
    TEIG(
        listOf(
            Fylkesnummer::class,
            Kommunenummer.Lopenummer::class,
            Matrikkelnummer.Gardsnummer::class,
            Matrikkelnummer.Bruksnummer::class,
            Matrikkelnummer.Festenummer::class,
            Matrikkelnummer.Seksjonsnummer::class,
            TeigId::class,
        ),
    ),
}

private fun Ident.form(): Form? {
    val komponenter = type.types.map { it.classifier }
    return Form.entries.firstOrNull { it.komponenter == komponenter }
}

private val ReformOp.nøkkel: Ident.NonEmpty
    get() = when (this) {
        is ReformOp.SourceOp -> source
        is ReformOp.NewEntity -> target
    }

@Suppress("UNCHECKED_CAST")
private fun <T> Ident.komponent(indeks: Int): T = toArray()[indeks] as T

private fun Ident.fylke() = komponent<Fylkesnummer>(0)

private fun Ident.lopenummer() = komponent<Kommunenummer.Lopenummer>(1)

private fun Ident.gardsnummer() = komponent<Matrikkelnummer.Gardsnummer>(2)

private fun Ident.bruksnummer() = komponent<Matrikkelnummer.Bruksnummer>(3)

private fun Ident.kommunenummer() = Kommunenummer(fylke(), lopenummer())

/** Én fylke- eller kommunekilde med målene den gir opphav til. */
private class Kildeendring(val kilde: ReformOp.SourceOp, val mål: List<Ident.NonEmpty>)

private class EndringMapper(
    private val claims: ReformClaims,
    private val fylker: Map<Fylkesnummer, Fylke>,
    private val kommuner: Map<Kommunenummer, Kommune>,
) {
    private val ops = claims.declarations
    private val nyeEntiteter = ops.filterIsInstance<ReformOp.NewEntity>().associateBy { it.target }
    private val opprettelser = ops.filterIsInstance<ReformOp.Create>().map { it.target }.toSet()
    private val feil = mutableListOf<Umappbar>()

    /** Operasjoner som enten er mappet eller allerede er rapportert som umappbare. */
    private val behandlet = mutableSetOf<ReformOp>()

    fun endring(id: String, navn: String): Either<NonEmptyList<Umappbar>, EndringDTO> {
        val fylkeendringer = kildeendringer(Form.FYLKE)
        val kommuneendringer = kildeendringer(Form.KOMMUNE)

        val transformasjoner = fylkeendringer.map { endring ->
            FylkeTransformasjonDTO(
                fylkesnummer = FraEnTilMangeDTO(endring.kilde.source.fylke().verdi(), endring.mål.map { it.fylke().verdi() }),
                sammenslaa = sammenslaa(endring, fylkeendringer),
            )
        } + kommuneendringer.map { endring ->
            KommuneTransformasjonDTO(
                fylkesnummer = FraEnTilMangeDTO(
                    endring.kilde.source.fylke().verdi(),
                    listOf(endring.mål.first().fylke().verdi()),
                ),
                kommuneløpenummer = FraEnTilMangeDTO(
                    endring.kilde.source.lopenummer().verdi(),
                    endring.mål.map { it.lopenummer().verdi() },
                ),
                sammenslaa = sammenslaa(endring, kommuneendringer),
            )
        } + veger() + enkleFlyttinger(Form.VEGADRESSE) + enkleFlyttinger(Form.KRETS) + matrikkelenheter() + teiger()

        val fylkemål = fylkeendringer.flatMap { it.mål }.distinct()
        val kommunemål = kommuneendringer.flatMap { it.mål }.distinct()
        // Opprettelsene hører til målene, og er dermed mappet
        behandlet += nyeEntiteter.filterKeys { it in fylkemål || it in kommunemål }.values.filterIsInstance<ReformOp.Create>()

        val nyeFylker = fylkemål.mapNotNull { fylkeDto(it) }
        val nyeKommuner = kommunemål.mapNotNull { kommuneDto(it) }
        rapporterUbehandlede()

        feil.toNonEmptyListOrNull()?.let { return it.left() }
        return EndringDTO(
            id = id,
            navn = navn,
            type = if (fylkeendringer.isEmpty()) "kommune" else "fylke",
            utgåendeFylker = fylkeendringer.mapNotNull { fylker[it.kilde.source.fylke()] }.map { FylkeDTO(it.fylkesnavn.name, it.fylkesnummer.verdi()) },
            utgåendeKommuner = kommuneendringer.mapNotNull { kommuner[it.kilde.source.kommunenummer()] }.map {
                EnkelKommuneDTO(
                    it.kommunenavn.name,
                    it.kommunenummer.fylkesnummer.verdi(),
                    it.kommunenummer.lopenummer.verdi(),
                )
            },
            nyeFylker = nyeFylker,
            nyeKommuner = nyeKommuner,
            transformasjoner = transformasjoner,
        ).right()
    }

    private fun avvis(op: ReformOp, grunn: String): Nothing? {
        feil += Umappbar.IngenDtoEkvivalent(listOf(op), grunn)
        behandlet += op
        return null
    }

    private fun avledninger(kilde: Ident.NonEmpty) = claims.derives[kilde].orEmpty()

    /**
     * Kildene for fylker og kommuner med målene. Mål er etterfølgeren (bare kommuner) og alle avledninger, eller
     * målet til en [ReformOp.RetireTo].
     */
    private fun kildeendringer(form: Form): List<Kildeendring> =
        ops.filterIsInstance<ReformOp.SourceOp>().filter { it.source.form() == form }.mapNotNull { op ->
            val avledninger = avledninger(op.source)
            val mål = when (op) {
                is ReformOp.Move -> return@mapNotNull avvis(op, "En endring oppretter alltid en ny $form, så Move finnes ikke")
                is ReformOp.RetireTo -> {
                    if (avledninger.any { it.target != op.target }) {
                        return@mapNotNull avvis(op, "Avledninger til andre mål enn $form-målet kan ikke uttrykkes")
                    }
                    listOf(op.target)
                }
                is ReformOp.Retire -> {
                    if ((form == Form.KOMMUNE) != (op.successor != null)) {
                        return@mapNotNull avvis(op, "Deling av $form har ${if (form == Form.KOMMUNE) "alltid" else "aldri"} etterfølger")
                    }
                    if (op.successor != null && avledninger.none { it.target == op.successor }) {
                        behandlet += avledninger
                        return@mapNotNull avvis(
                            op,
                            "Deling av $form der etterfølgeren ikke opprettes av delingen kan ikke uttrykkes: " +
                                "DTO-en oppretter alle målene ved deling",
                        )
                    }
                    (listOfNotNull(op.successor) + avledninger.map { it.target }).distinct().also {
                        if (it.size < 2) return@mapNotNull avvis(op, "Deling av $form må ha minst to mål")
                    }
                }
            }
            if (form == Form.KOMMUNE && mål.map { it.fylke() }.distinct().size > 1) {
                return@mapNotNull avvis(op, "Kommunemål i flere fylker kan ikke uttrykkes")
            }
            if (mål.any { it.form() != form }) {
                return@mapNotNull avvis(op, "Målet har ikke samme identtype som kilden")
            }
            behandlet += op
            behandlet += avledninger
            Kildeendring(op, mål)
        }

    /**
     * `sammenslaa = false` oppretter målene. Et mål opprettes én gang: delinger og avledninger eier målene sine, og
     * av flere sammenslåinger inn i samme opprettede mål er det bare den første som eier det.
     */
    private fun sammenslaa(endring: Kildeendring, alle: List<Kildeendring>): Boolean {
        if (endring.mål.size > 1) return false
        val mål = endring.mål.single()
        if (avledninger(endring.kilde.source).any { it.target == mål }) return false
        if (mål !in opprettelser) return true

        val medSammeMål = alle.filter { mål in it.mål }
        return medSammeMål.any { it.mål.size > 1 } || medSammeMål.first() !== endring
    }

    private fun fylkeDto(mål: Ident.NonEmpty): FylkeDTO? {
        val entitet = nyeEntiteter[mål] ?: return fylker[mål.fylke()]
            ?.let { FylkeDTO(it.fylkesnavn.name, it.fylkesnummer.verdi()) }
            ?: run { feil += Umappbar.FinnesIkke(mål); null }
        val data = entitet.payload() as? Fylkesdata
            ?: run { feil += Umappbar.UgyldigPayload(mål, entitet.payload(), "Forventet Fylkesdata"); return null }
        return FylkeDTO(data.navn, mål.fylke().verdi())
    }

    private fun kommuneDto(mål: Ident.NonEmpty): KommuneDTO? {
        val entitet = nyeEntiteter[mål]
            ?: return kommuner[mål.kommunenummer()]?.tilDto() ?: run { feil += Umappbar.FinnesIkke(mål); null }
        val data = entitet.payload() as? Kommunedata
            ?: run { feil += Umappbar.UgyldigPayload(mål, entitet.payload(), "Forventet Kommunedata"); return null }
        val vapen = data.kommunevapen
            ?: run { feil += Umappbar.UgyldigPayload(mål, data, "Kommunevåpen mangler"); return null }
        val gardsnumre = Either.catch { godkjenteGardsnumreTilListe(data.godkjenteGardsnumre) }
            .getOrElse { feil += Umappbar.UgyldigPayload(mål, data, "Ugyldige godkjente gårdsnumre: $it"); return null }
        return KommuneDTO(
            navn = data.navn.uppercase(),
            fylkesnummer = mål.fylke().verdi(),
            kommunenummer = mål.lopenummer().verdi(),
            gyldigTilDato = null,
            koordinatsystem = data.koordinatsystem.tilDto(),
            senterpunkt = KoordinatDTO(data.senterpunkt.x, data.senterpunkt.y),
            nedsattKonsesjonsgrense = data.nedsattKonsesjonsgrense,
            godkjenteGardsnumre = gardsnumre,
            adresse = data.adresse?.let { AdresseDTO(it.adresselinje1, it.adresselinje2, it.postnummer, "") },
            standardRekvirent = data.standardRekvirentOrgnummer?.let { StandardRekvirentDTO(it, "") },
            kommunevapen = Base64.getEncoder().encodeToString(vapen),
        )
    }

    private fun Kommune.tilDto() = KommuneDTO(
        navn = kommunenavn.name.uppercase(),
        fylkesnummer = kommunenummer.fylkesnummer.verdi(),
        kommunenummer = kommunenummer.lopenummer.verdi(),
        gyldigTilDato = gyldigTilDato,
        koordinatsystem = koordinatsystem.tilDto(),
        senterpunkt = KoordinatDTO(senterpunkt.x, senterpunkt.y),
        nedsattKonsesjonsgrense = nedsattKonsesjonsgrense,
        godkjenteGardsnumre = godkjenteGardsnumreTilListe(godkjenteGardsnumre),
        adresse = adresse?.let { AdresseDTO(it.adresselinje1, it.adresselinje2, it.postnummer, it.poststed) },
        standardRekvirent = standardRekvirent?.let { StandardRekvirentDTO(it.orgnummer, it.navn) },
        kommunevapen = Base64.getEncoder().encodeToString(kommunevapen),
    )

    private fun Koordinatsystem.tilDto() = when (this) {
        Koordinatsystem.UTM32 -> KoordinatsystemDTO.UTM32
        Koordinatsystem.UTM33 -> KoordinatsystemDTO.UTM33
        Koordinatsystem.UTM35 -> KoordinatsystemDTO.UTM35
        Koordinatsystem.UKJENT -> KoordinatsystemDTO.UKJENT
    }

    private fun ReformOp.NewEntity.payload(): Payload? = when (this) {
        is ReformOp.Create -> payload
        is ReformOp.Derive -> payload
    }

    /** Flytting av veg, og delinger av veg (avledninger fra flyttingen). Alle målene må ligge i samme fylke. */
    private fun veger(): List<TransformasjonDTO> =
        ops.filterIsInstance<ReformOp.Move>().filter { it.source.form() == Form.VEG }.mapNotNull { flytting ->
            val avledninger = avledninger(flytting.source)
            val mål = listOf(flytting.target) + avledninger.map { it.target }
            if (mål.any { it.form() != Form.VEG }) {
                return@mapNotNull avvis(flytting, "Målet har ikke samme identtype som kilden")
            }
            if (mål.map { it.fylke() }.distinct().size > 1) {
                return@mapNotNull avvis(flytting, "Vegdeling med mål i flere fylker kan ikke uttrykkes")
            }
            behandlet += flytting
            behandlet += avledninger
            VegTransformasjonDTO(
                fylkesnummer = FraEnTilMangeDTO(flytting.source.fylke().verdi(), listOf(mål.first().fylke().verdi())),
                kommuneløpenummer = FraEnTilMangeDTO(flytting.source.lopenummer().verdi(), mål.map { it.lopenummer().verdi() }),
                adressekode = FraEnTilMangeDTO(
                    flytting.source.komponent<Adressekode>(2).value.toString(),
                    mål.map { it.komponent<Adressekode>(2).value.toString() },
                ),
            )
        }

    /** Flytting av vegadresser og kretser. */
    private fun enkleFlyttinger(form: Form): List<TransformasjonDTO> =
        ops.filterIsInstance<ReformOp.Move>().filter { it.source.form() == form }.mapNotNull { flytting ->
            if (flytting.target.form() != form) {
                return@mapNotNull avvis(flytting, "Målet har ikke samme identtype som kilden")
            }
            behandlet += flytting
            val fylke = FraTilDTO(flytting.source.fylke().verdi(), flytting.target.fylke().verdi())
            val kommune = FraTilDTO(flytting.source.lopenummer().verdi(), flytting.target.lopenummer().verdi())
            fun fraTil(indeks: Int, tekst: (Comparable<*>) -> String) =
                FraTilDTO(tekst(flytting.source.toArray()[indeks]), tekst(flytting.target.toArray()[indeks]))
            when (form) {
                Form.VEGADRESSE -> VegadresseTransformasjonDTO(
                    fylke, kommune,
                    adressekode = fraTil(2) { (it as Adressekode).value.toString() },
                    adressenummer = fraTil(3) { (it as Adressenummernummer).value.toString() },
                )
                else -> KretsTransformasjonDTO(
                    fylke, kommune,
                    kretstype = fraTil(2) { (it as Kretstype).kretstype },
                    kretsnummer = fraTil(3) { (it as Kretsnummer).value.toString() },
                )
            }
        }

    /**
     * Én transformasjon per gårdsnummerserie som berøres, enten av en regel for serien eller av flytting av
     * grunneiendommer i den. Serien utgår når gårdsnummeret til er 0.
     */
    private fun matrikkelenheter(): List<TransformasjonDTO> {
        val grunneiendommer = ops.filterIsInstance<ReformOp.Move>()
            .filter { it.source.form() == Form.GRUNNEIENDOM && it.target.form() == Form.GRUNNEIENDOM }
        val serieregler = ops.filterIsInstance<ReformOp.SourceOp>().filter { it.source.form() == Form.SERIE }
        fun ReformOp.Move.serie() = source.dropLast() as Ident.NonEmpty

        return (serieregler.map { it.source } + grunneiendommer.map { it.serie() }).distinct().mapNotNull { serie ->
            val regel = serieregler.firstOrNull { it.source == serie }
            val barn = grunneiendommer.filter { it.serie() == serie }
            val til: Ident.NonEmpty? = when {
                regel == null -> serie
                regel is ReformOp.Move && regel.target.form() == Form.SERIE -> regel.target
                regel is ReformOp.Retire && regel.successor == null && barn.isNotEmpty() -> null
                else -> return@mapNotNull avvis(
                    regel,
                    "Bare Move av en serie, og Retire av en serie med flytting av bruksnumre, kan uttrykkes",
                )
            }
            behandlet += listOfNotNull(regel)
            behandlet += barn

            MatrikkelenhetTransformasjonDTO(
                fylkesnummer = FraTilDTO(serie.fylke().verdi(), (til ?: serie).fylke().verdi()),
                kommuneløpenummer = FraTilDTO(serie.lopenummer().verdi(), (til ?: serie).lopenummer().verdi()),
                gårdsnummer = FraTilDTO(serie.gardsnummer().value.toString(), (til?.gardsnummer()?.value ?: 0).toString()),
                bruksnummer = barn.associate { flytting ->
                    flytting.source.bruksnummer().value to TilGrunneiendomDTO(
                        fylkesnummer = flytting.target.fylke().value,
                        kommunelopenummer = flytting.target.lopenummer().value,
                        gardsnummer = flytting.target.gardsnummer().value,
                        bruksnummer = flytting.target.bruksnummer().value,
                    )
                }.ifEmpty { null },
            )
        }
    }

    /**
     * Teiger flyttes alltid på «mnr mangler» (bruksnummer 0) og «mnr vann mangler» (bruksnummer 1), uten gårdsnummer,
     * festenummer og seksjonsnummer. Begge må flyttes til samme teig.
     */
    private fun teiger(): List<TransformasjonDTO> {
        fun Ident.erTeigUtenMatrikkelnummer() =
            gardsnummer().value == 0 &&
                komponent<Matrikkelnummer.Festenummer>(4).value == 0.toShort() &&
                komponent<Matrikkelnummer.Seksjonsnummer>(5).value == 0.toShort()

        val flyttinger = ops.filterIsInstance<ReformOp.Move>().filter { flytting ->
            flytting.source.form() == Form.TEIG && flytting.target.form() == Form.TEIG &&
                flytting.source.erTeigUtenMatrikkelnummer() && flytting.target.erTeigUtenMatrikkelnummer() &&
                flytting.source.bruksnummer() == flytting.target.bruksnummer()
        }

        fun Ident.teig() = Triple(fylke(), lopenummer(), komponent<TeigId>(6))

        return flyttinger.groupBy { it.source.teig() to it.target.teig() }.mapNotNull { (fraTil, flyttinger) ->
            val (fra, til) = fraTil
            if (flyttinger.map { it.source.bruksnummer().value.toInt() }.toSet() != setOf(0, 1)) return@mapNotNull null
            behandlet += flyttinger
            TeigTransformasjonDTO(
                fylkesnummer = FraTilDTO(fra.first.verdi(), til.first.verdi()),
                kommuneløpenummer = FraTilDTO(fra.second.verdi(), til.second.verdi()),
                teigId = FraTilDTO(fra.third.id.toString(), til.third.id.toString()),
            )
        }
    }

    private fun rapporterUbehandlede() {
        (ops - behandlet).forEach { op ->
            val grunn = when (op) {
                is ReformOp.Derive ->
                    if (ops.none { it is ReformOp.SourceOp && it.source == op.from }) {
                        "Utskilling, der kilden fortsetter, kan ikke uttrykkes i DTO-en"
                    } else {
                        "Avledningen hører ikke til en kilderegel som kan uttrykkes"
                    }

                is ReformOp.Create -> "Ingen fylke- eller kommuneendring har målet"
                else -> "Operasjonen på ${op.nøkkel.form() ?: "ukjent identtype"} kan ikke uttrykkes"
            }
            feil += Umappbar.IngenDtoEkvivalent(listOf(op), grunn)
        }
    }
}
