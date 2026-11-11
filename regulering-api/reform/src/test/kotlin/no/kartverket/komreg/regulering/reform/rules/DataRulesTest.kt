package no.kartverket.komreg.regulering.reform.rules

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Adressekode
import no.kartverket.komreg.integration.spi.identTypeOf3
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformOp
import kotlin.test.Test

class DataRulesTest {
    private val expired = LocalDate(2020, 1, 1)

    private fun kommune(nummer: Long, gyldigTil: LocalDate? = null) = Kommune(
        nummer, "K$nummer", gyldigTil, Koordinatsystem.UTM32, Koordinat(0.0, 0.0), false, "", null, null, ByteArray(0),
    )

    private val service = object : KommuneService {
        override fun findAlleFylker() = listOf(Fylke(30, "A", null), Fylke(31, "B", expired), Fylke(32, "C", null))
        override fun findAlleKommuner() = listOf(
            kommune(3001), kommune(3002), kommune(3003, expired), kommune(3201), kommune(3101, expired),
        )
    }

    private fun fylke(f: Long) = Fylke.Ident(Fylkesnummer(f))
    private fun kommune(f: Long, k: Int) = Kommune.KommuneIdent(Fylkesnummer(f), Kommunenummer.Lopenummer(k.toByte()))

    private fun errors(vararg ops: ReformOp): List<DataErr> {
        val claims = ReformClaims.validate(ops.toList()).fold({ error("Ugyldige claims: $it") }, { it })
        return runBlocking { claims.validateData(service) }.leftOrNull()?.toList().orEmpty()
    }

    private fun uncovered(errors: List<DataErr>) =
        errors.filterIsInstance<DataErr.MunicipalityNotCovered>().map { it.municipality }

    @Test
    fun `fylke som utgår krever egen regel for hver gyldig kommune`() {
        val errors = errors(
            ReformOp.RetireTo(fylke(30), fylke(32)),
            ReformOp.RetireTo(kommune(30, 1), kommune(32, 1)),
        )
        assertThat(uncovered(errors)).containsExactlyInAnyOrder(kommune(30, 2))
        assertThat(errors.map { it.rule }).containsExactlyInAnyOrder("MunicipalityNotCovered")
    }

    @Test
    fun `fylke med dekning er gyldig, og utgåtte kommuner trenger ingen regel`() {
        assertThat(
            errors(
                ReformOp.RetireTo(fylke(30), fylke(32)),
                ReformOp.RetireTo(kommune(30, 1), kommune(32, 1)),
                ReformOp.Move(kommune(30, 2), kommune(32, 2)),
            ),
        ).isEmpty()
    }

    @Test
    fun `regel for fylket dekker ikke kommunene`() {
        assertThat(uncovered(errors(ReformOp.Retire(fylke(30))))).containsExactlyInAnyOrder(
            kommune(30, 1),
            kommune(30, 2),
        )
    }

    @Test
    fun `kommunemål som må finnes, finnes ikke eller er utgått`() {
        assertThat(
            errors(
                ReformOp.RetireTo(kommune(30, 1), kommune(32, 1)),
                ReformOp.RetireTo(kommune(30, 2), kommune(32, 99)),
                ReformOp.RetireTo(kommune(30, 4), kommune(31, 1)),
            ).map { it.rule },
        ).containsExactlyInAnyOrder("TargetNotFound", "SourceNotFound", "TargetExpired")
    }

    @Test
    fun `eksisterende etterfølger ved kommunedeling må finnes og være gyldig`() {
        assertThat(
            errors(
                ReformOp.Retire(kommune(30, 1), successor = kommune(30, 2)),
                ReformOp.Derive(kommune(30, 5), kommune(30, 1), null),
            ),
        ).isEmpty()
        assertThat(
            errors(
                ReformOp.Retire(kommune(30, 1), successor = kommune(30, 4)),
                ReformOp.Derive(kommune(30, 5), kommune(30, 1), null),
                ReformOp.Retire(kommune(30, 2), successor = kommune(30, 3)),
                ReformOp.Derive(kommune(30, 6), kommune(30, 2), null),
            ).map { it.rule },
        ).containsExactlyInAnyOrder("TargetNotFound", "TargetExpired")
    }

    @Test
    fun `fylkemål som må finnes, finnes ikke eller er utgått`() {
        val errors = errors(
            ReformOp.RetireTo(fylke(32), fylke(99)),
            ReformOp.RetireTo(fylke(30), fylke(31)),
        )
        assertThat(errors.filterNot { it is DataErr.MunicipalityNotCovered }.map { it.rule })
            .containsExactlyInAnyOrder("TargetNotFound", "TargetExpired")
    }

    @Test
    fun `nye fylker og kommuner kan ikke ta et nummer som er i bruk`() {
        assertThat(
            errors(
                ReformOp.Create(fylke(32), Fylkesdata("C")),
                ReformOp.Derive(kommune(30, 2), from = kommune(30, 1)),
            ).map { it.rule },
        ).containsExactlyInAnyOrder("TargetInUse", "TargetInUse")
        assertThat(
            errors(
                ReformOp.Create(fylke(31), Fylkesdata("B")),
                ReformOp.Derive(kommune(30, 3), from = kommune(30, 1)),
            ),
        ).isEmpty()
    }

    @Test
    fun `kilder som ikke finnes eller er utgått avvises`() {
        assertThat(
            errors(
                ReformOp.Move(kommune(30, 1), kommune(33, 1)),
                ReformOp.Move(kommune(30, 3), kommune(33, 2)),
                ReformOp.Move(kommune(30, 7), kommune(33, 3)),
                ReformOp.Derive(kommune(33, 4), from = kommune(31, 1)),
            ).map { it.rule },
        ).containsExactlyInAnyOrder("SourceExpired", "SourceNotFound", "SourceExpired")
    }

    @Test
    fun `andre identtyper sjekkes ikke`() {
        assertThat(errors(ReformOp.Move(veg(99, 99, 1), veg(98, 98, 1)))).isEmpty()
    }

    private fun veg(f: Long, k: Int, kode: Int) = vegType(Fylkesnummer(f), Kommunenummer.Lopenummer(k.toByte()), Adressekode(kode))
    private val vegType = runBlocking { identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, Adressekode>() }
}
