package no.kartverket.komreg.transformation.reform

import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.core.domain.Adressenummernummer
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Kretsnummer
import no.kartverket.komreg.core.domain.Kretstype
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.domain.TeigId
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.identTypeOf2
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.identTypeOf4
import no.kartverket.komreg.integration.spi.identTypeOf7
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Kretsendring
import no.kartverket.komreg.transformation.Matrikkelenhet
import no.kartverket.komreg.transformation.Matrikkelenhetendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.Teigendring
import no.kartverket.komreg.transformation.Vegadresseendring
import no.kartverket.komreg.transformation.Vegendring
import no.kartverket.komreg.transformation.tilFylkesdata
import no.kartverket.komreg.transformation.tilKommunedata

/**
 * Reglene for reguleringen. Gårdsnummerserier og veger som er mål, antas å finnes (DTO-en kan ikke opprette
 * dem i v1).
 *
 * Fylker og kommuner: hver kildeendring gir en skjebne for kilden (1:1 og sammenslåing: [ReformOp.RetireTo];
 * deling: [ReformOp.Retire], for kommuner med første mål som etterfølger). Et mål opprettes bare hvis minst
 * én kildeendring til det ikke er merket som sammenslåing; ellers må det finnes fra før. Et mål som opprettes
 * fra én kildeendring, avledes fra kilden ([ReformOp.Derive]); fra flere opprettes det én gang
 * ([ReformOp.Create]).
 *
 * Veger: 1:1 er [ReformOp.Move]. Ved deling flyttes vegen til første mål, og de øvrige avledes fra den.
 */
suspend fun Reguleringsinput.toReformOps(): List<ReformOp> {
    val fylkeMap = fylker.associateBy { it.fylkesnummer }
    val kommuneMap = kommuner.associateBy { it.kommunenummer }

    val sourceChanges = endringer.associateWith { endring ->
        when (endring) {
            is Fylkeendring -> {
                val type = identTypeOf1<Fylkesnummer>()
                SourceChange(
                    source = type(endring.fylkesnummer.fra),
                    targets = endring.fylkesnummer.til.map { type(it) to fylkeMap.getValue(it).tilFylkesdata() },
                    sammenslaaing = endring.sammenslaaing == true,
                    successorOnSplit = false,
                )
            }

            is Kommuneendring -> {
                val type = identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()
                val tilFylke = endring.fylkesnummer.til.single()
                SourceChange(
                    source = type(endring.fylkesnummer.fra, endring.kommuneløpenummer.fra),
                    targets = endring.kommuneløpenummer.til.map { tilKlnr ->
                        type(tilFylke, tilKlnr) to
                            kommuneMap.getValue(Kommunenummer(tilFylke, tilKlnr)).tilKommunedata(ikrafttredelsesdato)
                    },
                    sammenslaaing = endring.sammenslaaing == true,
                    successorOnSplit = true,
                )
            }

            else -> null
        }
    }

    val changesPerTarget = LinkedHashMap<Ident.NonEmpty, MutableList<SourceChange>>()
    sourceChanges.values.filterNotNull().forEach { change ->
        change.targets.map { it.first }.distinct().forEach { changesPerTarget.getOrPut(it) { mutableListOf() } += change }
    }
    val created = changesPerTarget.filterValues { changes -> changes.any { !it.sammenslaaing } }
    val derivedTargets = created.filterValues { it.size == 1 }.keys
    val createdTargets = created.filterValues { it.size > 1 }.keys

    val creates = sourceChanges.values.filterNotNull()
        .flatMap { it.targets }
        .filter { (target, _) -> target in createdTargets }
        .distinctBy { it.first }
        .map { (target, payload) -> ReformOp.Create(target, payload) }

    val ops = endringer.flatMap { endring ->
        when (endring) {
            is Fylkeendring, is Kommuneendring -> {
                val change = sourceChanges.getValue(endring)!!
                val fate = change.targets.singleOrNull()
                    ?.let { (target, _) -> ReformOp.RetireTo(change.source, target) }
                    ?: ReformOp.Retire(change.source, change.targets.first().first.takeIf { change.successorOnSplit })
                val derives = change.targets
                    .filter { (target, _) -> target in derivedTargets }
                    .map { (target, payload) -> ReformOp.Derive(target, from = change.source, payload) }
                listOf(fate) + derives
            }

            is Matrikkelenhetendring -> endring.toReformOps()

            is Vegendring -> endring.toReformOps()

            is Vegadresseendring -> {
                val type = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode, Adressenummernummer>()
                listOf(
                    ReformOp.Move(
                        type(
                            endring.fylkesnummer.fra,
                            endring.kommuneløpenummer.fra,
                            endring.adressekode.fra,
                            endring.adressenummer.fra,
                        ),
                        type(
                            endring.fylkesnummer.til,
                            endring.kommuneløpenummer.til,
                            endring.adressekode.til,
                            endring.adressenummer.til,
                        ),
                    ),
                )
            }

            is Kretsendring -> {
                val type = identTypeOf4<Fylkesnummer, Kommunenummer.Lopenummer, Kretstype, Kretsnummer>()
                listOf(
                    ReformOp.Move(
                        type(
                            endring.fylkesnummer.fra,
                            endring.kommuneløpenummer.fra,
                            endring.kretstype.fra,
                            endring.kretsnummer.fra,
                        ),
                        type(
                            endring.fylkesnummer.til,
                            endring.kommuneløpenummer.til,
                            endring.kretstype.til,
                            endring.kretsnummer.til,
                        ),
                    ),
                )
            }

            is Teigendring -> endring.toReformOps()
        }
    }

    return creates + ops
}

/** Endring av et fylke eller en kommune: én kilde til ett eller flere mål med payload. */
private class SourceChange(
    val source: Ident.NonEmpty,
    val targets: List<Pair<Ident.NonEmpty, Payload>>,
    val sammenslaaing: Boolean,
    val successorOnSplit: Boolean,
)

// Legacy `mapVegendring` slår opp adressekoden med `indexOf(tilKlnr)`, som gir feil kode når samme kommune står
// flere ganger. Her brukes indeksen direkte.
private suspend fun Vegendring.toReformOps(): List<ReformOp> {
    val type = identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>()
    val source = type(fylkesnummer.fra, kommuneløpenummer.fra, adressekode.fra)
    if (kommuneløpenummer.til.size == 1) {
        return listOf(
            ReformOp.Move(source, type(fylkesnummer.til.single(), kommuneløpenummer.til.single(), adressekode.til.first())),
        )
    }

    val targets = kommuneløpenummer.til.mapIndexed { i, tilKlnr ->
        type(fylkesnummer.til.single(), tilKlnr, adressekode.til.singleOrNull() ?: adressekode.til[i])
    }
    return listOf(ReformOp.Move(source, targets.first())) +
        targets.drop(1).map { ReformOp.Derive(it, from = source) }
}

private fun Matrikkelenhetendring.toReformOps(): List<ReformOp> {
    val fra = Matrikkelenhet.GardsnummerserieIdent(fylkesnummer.fra, kommuneløpenummer.fra, fraGardsnummer)
    val til = tilGardsnummer?.let {
        Matrikkelenhet.GardsnummerserieIdent(fylkesnummer.til, kommuneløpenummer.til, it)
    }
    if (til != fra) {
        val tilSerier = bruksnummer.values.mapTo(HashSet()) { it.dropLast() }
        til?.let(tilSerier::add)
        require(fra !in tilSerier) {
            "Matrikkelendringen er ugyldig, gårdsnummeret bevares for noen bruksnummer, men gårdsnummerserien er " +
                "satt til å endres: $fra går til $til"
        }
    }
    return buildList {
        when {
            til == null -> add(ReformOp.Retire(fra))
            til != fra -> add(ReformOp.Move(fra, til))
        }
        bruksnummer.forEach { (fraBruksnummer, tilGrunneiendom) ->
            add(ReformOp.Move(fra.appendWith(Matrikkelenhet.GrunneiendomIdent, fraBruksnummer), tilGrunneiendom))
        }
    }
}

// Teiger som flyttes, ligger alltid på «mnr mangler» (bnr 0) eller «mnr vann mangler» (bnr 1)
private suspend fun Teigendring.toReformOps(): List<ReformOp> {
    val type = identTypeOf7<
        Fylkesnummer,
        Kommunenummer.Lopenummer,
        Matrikkelnummer.Gardsnummer,
        Matrikkelnummer.Bruksnummer,
        Matrikkelnummer.Festenummer,
        Matrikkelnummer.Seksjonsnummer,
        TeigId,
        >()

    suspend fun key(fylke: Fylkesnummer, kommune: Kommunenummer.Lopenummer, bnr: Short, teig: TeigId) =
        type(
            fylke,
            kommune,
            Matrikkelnummer.Gardsnummer(0),
            Matrikkelnummer.Bruksnummer(bnr),
            Matrikkelnummer.Festenummer(0),
            Matrikkelnummer.Seksjonsnummer(0),
            teig,
        )

    return listOf<Short>(0, 1).map { bnr ->
        ReformOp.Move(
            key(fylkesnummer.fra, kommuneløpenummer.fra, bnr, teigId.fra),
            key(fylkesnummer.til, kommuneløpenummer.til, bnr, teigId.til),
        )
    }
}
