package no.kartverket.komreg.routes

import io.kotest.core.spec.style.AnnotationSpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.repositories.Kjoring
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.repositories.Kjoringstatus
import no.kartverket.komreg.repositories.Mottaker
import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.TransformationRepo
import no.kartverket.komreg.services.StedsnavnService
import no.kartverket.komreg.transformation.reform.reform
import java.io.StringWriter
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StedsnavnServiceTest : AnnotationSpec() {
    private val dato = LocalDate(2025, 1, 1)
    private val reguleringRepo = mockk<ReguleringRepo>()
    private val kjoringRepo = mockk<KjoringRepo>()
    private val service = StedsnavnService(reguleringRepo, kjoringRepo, mockk<TransformationRepo>())

    @Test
    fun `parameterfil for legacy-regulering`() {
        val regulering = LegacyRegulering(
            id = "regId",
            navn = "Legacy",
            dato = dato,
            endringer = listOf(
                EndringDTO(
                    id = "endringId",
                    navn = "Endring",
                    type = "fylke",
                    utgåendeFylker = emptyList(),
                    utgåendeKommuner = emptyList(),
                    nyeFylker = listOf(FylkeDTO("Nytt fylke", "31")),
                    nyeKommuner = listOf(kommuneDto("Ny kommune", "03", "03")),
                    transformasjoner = listOf(
                        FylkeTransformasjonDTO(FraEnTilMangeDTO("30", listOf("31"))),
                        KommuneTransformasjonDTO(
                            fylkesnummer = FraEnTilMangeDTO("03", listOf("03")),
                            kommuneløpenummer = FraEnTilMangeDTO("01", listOf("03")),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "DT\t2025-01-01",
                "NF\t31\tNytt fylke",
                "NK\t0303\tNy kommune",
                "AN\t<<JSON her>>",
                "UK\t0301\t0303",
                "UF\t30",
            ),
            parameterfil(regulering),
        )
    }

    @Test
    fun `parameterfil for ReformOp-regulering`() {
        val regulering = ReformOpRegulering(
            navn = "Reform",
            ikrafttredelse = dato,
            ops = reform {
                // Fylke 30 blir fylke 31
                retireTo(fylke(30), fylke(31))
                derive(fylke(31), fylke(30), Fylkesdata("Nytt fylke"))

                // Kommune 0301 og 0302 slås sammen til 0303
                create(kommune(3, 3), kommunedata("NY KOMMUNE"))
                retireTo(kommune(3, 1), kommune(3, 3))
                retireTo(kommune(3, 2), kommune(3, 3))

                // Kommune 0401 deles i 0402 og 0403, med 0402 som etterfølger
                retire(kommune(4, 1), kommune(4, 2))
                derive(kommune(4, 2), kommune(4, 1), kommunedata("DEL EN"))
                derive(kommune(4, 3), kommune(4, 1), kommunedata("DEL TO"))
            },
        )

        assertEquals(
            listOf(
                "DT\t2025-01-01",
                "NF\t31\tNytt fylke",
                "NK\t0303\tNY KOMMUNE",
                "NK\t0402\tDEL EN",
                "NK\t0403\tDEL TO",
                "AN\t<<JSON her>>",
                "UK\t0301\t0303",
                "UK\t0302\t0303",
                "UK\t0401\t0402",
                "UF\t30",
            ),
            parameterfil(regulering),
        )
    }

    @Test
    fun `ReformOp-regulering uten regler gir bare DT og AN`() {
        val regulering = ReformOpRegulering(navn = "Tom", ikrafttredelse = dato, ops = emptyList())

        assertEquals(listOf("DT\t2025-01-01", "AN\t<<JSON her>>"), parameterfil(regulering))
    }

    @Test
    fun `ReformOp-regulering avviser Move av kommune`() {
        val regulering = ReformOpRegulering(
            navn = "Move",
            ikrafttredelse = dato,
            ops = reform { move(kommune(3, 1), kommune(3, 2)) },
        )

        assertFailsWith<IllegalArgumentException> { parameterfil(regulering) }
    }

    @Test
    fun `ReformOp-regulering avviser ny kommune uten Kommunedata`() {
        val regulering = ReformOpRegulering(
            navn = "Feil payload",
            ikrafttredelse = dato,
            ops = reform {
                create(kommune(3, 3), Fylkesdata("Feil"))
                retireTo(kommune(3, 1), kommune(3, 3))
            },
        )

        assertFailsWith<IllegalArgumentException> { parameterfil(regulering) }
    }

    private fun parameterfil(regulering: Regulering): List<String> = runBlocking {
        every { reguleringRepo.getReguleringById("regId") } returns regulering
        every { kjoringRepo.getKjoring(1) } returns
            Kjoring(1, "regId", Date(), null, Kjoringstatus.FERDIG, Mottaker.DB_MATRIKKEL_MOTTAKER1)

        val writer = StringWriter()
        service.createParameterWriter(1)!!.invoke(writer)
        writer.toString().lines().filter { it.isNotEmpty() }
    }

    private fun fylke(nummer: Int): Ident.NonEmpty = Fylke.Ident(Fylkesnummer(nummer.toLong()))

    private fun kommune(fylkesnummer: Int, lopenummer: Int): Ident.NonEmpty =
        Kommune.KommuneIdent(Fylkesnummer(fylkesnummer.toLong()), Kommunenummer.Lopenummer(lopenummer.toByte()))

    private fun kommunedata(navn: String) = Kommunedata(
        navn = navn,
        koordinatsystem = Koordinatsystem.UTM33,
        senterpunkt = Koordinat(0.0, 0.0),
        nedsattKonsesjonsgrense = false,
        godkjenteGardsnumre = "1-100",
        adresse = null,
        standardRekvirentOrgnummer = null,
        ikrafttredelsesdato = dato,
        kommunevapen = null,
    )

    private fun kommuneDto(navn: String, fylkesnummer: String, kommunenummer: String) = KommuneDTO(
        navn = navn,
        fylkesnummer = fylkesnummer,
        kommunenummer = kommunenummer,
        gyldigTilDato = null,
        koordinatsystem = KoordinatsystemDTO.UTM33,
        senterpunkt = KoordinatDTO(0.0, 0.0),
        nedsattKonsesjonsgrense = false,
        godkjenteGardsnumre = emptyList(),
        adresse = null,
        standardRekvirent = null,
        kommunevapen = "",
    )
}
