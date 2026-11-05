package no.kartverket.komreg.regulering.api.server.persistence


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.kartverket.komreg.regulering.api.server.domain.type.TitleNumber
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM_CADASTRAL_UNIT
import no.kartverket.komreg.regulering.api.server.persistence.jooq.tables.references.REFORM_CADASTRAL_UNIT_PARCEL
import no.kartverket.komreg.regulering.api.server.route.regulering.GrensejusteringDraft
import no.kartverket.komreg.regulering.api.server.route.regulering.MatrikkelenhetDraft
import no.kartverket.komreg.regulering.api.server.route.regulering.OpprettReguleringRequest
import no.kartverket.komreg.regulering.api.server.route.regulering.ReguleringDetail
import org.jooq.DSLContext
import org.jooq.impl.DSL.multiset
import org.jooq.impl.DSL.row
import org.jooq.impl.DSL.select
import org.jooq.kotlin.coroutines.transactionCoroutine

class ReformRepository(
    private val sql: DSLContext
) {
    fun all(): Flow<ReguleringDetail> {
        val u = REFORM_CADASTRAL_UNIT
        val p = REFORM_CADASTRAL_UNIT_PARCEL
        val units = multiset(
            select(
                u.MUNICIPALITY_NUMBER,
                u.LAND_NUMBER,
                u.TITLE_NUMBER,
                u.NEW_MUNICIPALITY_NUMBER,
                u.NEW_LAND_NUMBER,
                u.NEW_TITLE_NUMBER,
                multiset(select(p.PARCEL_ID).from(p).where(p.REFORM_CADASTRAL_UNIT_ID.eq(u.ID))),
            )
                .from(u)
                .where(u.REFORM_ID.eq(REFORM.ID))
        )

        return sql.select(REFORM.ID, REFORM.NAME, REFORM.DATE, units)
            .from(REFORM)
            .asFlow()
            .map { (id, name, date, units) ->
                val grensejusteringer = units
                    .groupBy({ (fra, _, _, til) -> fra!! to til!! }) { (municipality, land, title, _, newLand, newTitle, parcels) ->
                        MatrikkelenhetDraft(
                            kommunenummer = municipality!!,
                            gardsnummer = land!!,
                            bruksnummer = title?.toInt(),
                            teigIder = parcels.mapTo(hashSetOf()) { it.value1()!! },
                            nyGardsnummer = newLand,
                            nyBruksnummer = newTitle?.toInt(),
                        )
                    }
                    .map { (k, enheter) -> GrensejusteringDraft(k.first, k.second, enheter.toSet()) }
                // The API has one grensejustering per regulering, and insert() always writes exactly one
                val grensejustering = checkNotNull(grensejusteringer.singleOrNull()) {
                    "Reform $id has ${grensejusteringer.size} grensejusteringer, expected exactly one"
                }
                ReguleringDetail(id!!, name!!, date!!.toKotlinLocalDate(), grensejustering)
            }
    }

    suspend fun insert(regulering: OpprettReguleringRequest): ReguleringDetail {
        val grensejusteringer = listOf(regulering.grensejustering)
        // Grensejusteringer are derived from their units when read back, so an empty one would be lost
        require(grensejusteringer.all { it.matrikkelenheter.isNotEmpty() }) {
            "Each grensejustering must contain at least one matrikkelenhet"
        }
        grensejusteringer.forEach { g ->
            require(g.matrikkelenheter.all { it.kommunenummer == g.fraKommunenummer }) {
                "All matrikkelenheter must be in fraKommunenummer ${g.fraKommunenummer}"
            }
        }

        val enheter = grensejusteringer.flatMap { g ->
            g.matrikkelenheter.map { enhet ->
                require(enhet.bruksnummer != null || enhet.nyBruksnummer == null) {
                    "nyBruksnummer requires bruksnummer for ${enhet.kommunenummer}-${enhet.gardsnummer}"
                }
                Triple(g, enhet, enhet.bruksnummer?.let { bnr -> TitleNumber(bnr).value })
            }
        }
        // (municipality_number, land_number, title_number) is unique per reform (ex_reform_source_cadastral_unit)
        val byKey = enheter.associate { (_, enhet, title) -> Triple(enhet.kommunenummer, enhet.gardsnummer, title) to enhet }

        return sql.transactionCoroutine { cfg ->
            val tx = cfg.dsl()
            val cu = REFORM_CADASTRAL_UNIT
            val cup = REFORM_CADASTRAL_UNIT_PARCEL

            val id = tx.insertInto(REFORM, REFORM.NAME, REFORM.DATE)
                .values(regulering.navn, regulering.dato.toJavaLocalDate())
                .returningResult(REFORM.ID)
                .awaitSingle()
                .value1()!!

            val result = ReguleringDetail(id, regulering.navn, regulering.dato, regulering.grensejustering)
            if (enheter.isEmpty()) return@transactionCoroutine result

            val inserted = tx.insertInto(
                cu,
                cu.REFORM_ID,
                cu.MUNICIPALITY_NUMBER,
                cu.LAND_NUMBER,
                cu.TITLE_NUMBER,
                cu.NEW_MUNICIPALITY_NUMBER,
                cu.NEW_LAND_NUMBER,
                cu.NEW_TITLE_NUMBER,
            )
                .valuesOfRows(enheter.map { (g, enhet, title) ->
                    row(
                        id,
                        enhet.kommunenummer,
                        enhet.gardsnummer,
                        title,
                        g.tilKommunenummer,
                        enhet.nyGardsnummer ?: enhet.gardsnummer,
                        enhet.bruksnummer?.let { bnr -> TitleNumber(enhet.nyBruksnummer ?: bnr).value },
                    )
                })
                .returningResult(cu.ID, cu.MUNICIPALITY_NUMBER, cu.LAND_NUMBER, cu.TITLE_NUMBER)
                .asFlow()
                .toList()

            // RETURNING order isn't guaranteed, so match units back to drafts by natural key
            val parcels = inserted.flatMap { (unitId, municipality, land, title) ->
                byKey.getValue(Triple(municipality!!, land!!, title)).teigIder.map { row(unitId!!, it) }
            }
            if (parcels.isNotEmpty()) {
                tx.insertInto(cup, cup.REFORM_CADASTRAL_UNIT_ID, cup.PARCEL_ID)
                    .valuesOfRows(parcels)
                    .awaitSingle()
            }

            result
        }
    }
}
