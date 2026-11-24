package no.kartverket.komreg.services

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Fylkesnavn
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.transformation.Endring
import no.kartverket.komreg.transformation.FraEnTilMange
import no.kartverket.komreg.transformation.Fylkeendring
import no.kartverket.komreg.transformation.Kommuneendring
import no.kartverket.komreg.transformation.Reguleringsinput
import no.kartverket.komreg.transformation.reform.toReformOps
import kotlin.test.Test
import kotlin.test.assertEquals

class StedsnavnParametreTest {
    private val dato = LocalDate(2024, 1, 1)

    private fun kommune(f: Long, k: Int, navn: String) = Kommune(
        kommunenummer = Kommunenummer(Fylkesnummer(f), Kommunenummer.Lopenummer(k.toByte())),
        kommunenavn = Kommunenavn(navn),
        gyldigTilDato = null,
        koordinatsystem = Koordinatsystem.UTM32,
        senterpunkt = Koordinat(1.0, 2.0),
        nedsattKonsesjonsgrense = false,
        godkjenteGardsnumre = "",
        adresse = null,
        standardRekvirent = null,
        kommunevapen = ByteArray(0),
    )

    private fun kommuneendring(fra: Pair<Int, Int>, til: List<Pair<Int, Int>>, sammenslaaing: Boolean = false) =
        Kommuneendring(
            FraEnTilMange(Fylkesnummer(fra.first.toLong()), til.map { Fylkesnummer(it.first.toLong()) }.distinct()),
            FraEnTilMange(Kommunenummer.Lopenummer(fra.second.toByte()), til.map { Kommunenummer.Lopenummer(it.second.toByte()) }),
            sammenslaaing,
        )

    private fun input(fylker: List<Fylke>, kommuner: List<Kommune>, vararg endringer: Endring) =
        Reguleringsinput("reg", dato, endringer.toList(), fylker, kommuner)

    private fun reform(input: Reguleringsinput) =
        stedsnavnParametre(runBlocking { input.toReformOps() }, input.ikrafttredelsesdato)

    @Test
    fun `reform gir samme parametre som legacy for nye fylker og kommuner`() {
        val input = input(
            listOf(Fylke(Fylkesnummer(31), Fylkesnavn("Østfold"), null)),
            listOf(kommune(31, 1, "Halden"), kommune(31, 50, "Ny"), kommune(31, 51, "Delt a"), kommune(31, 52, "Delt B")),
            Fylkeendring(FraEnTilMange(Fylkesnummer(30), listOf(Fylkesnummer(31)))),
            kommuneendring(30 to 1, listOf(31 to 1)),
            kommuneendring(30 to 35, listOf(31 to 50)),
            kommuneendring(30 to 36, listOf(31 to 50)),
            kommuneendring(30 to 40, listOf(31 to 51, 31 to 52)),
        )

        assertEquals(input.stedsnavnParametre(), reform(input))
        assertEquals(
            listOf(
                "DT\t2024-01-01",
                "NF\t31\tØstfold",
                "NK\t3101\tHALDEN",
                "NK\t3150\tNY",
                "NK\t3151\tDELT A",
                "NK\t3152\tDELT B",
                "AN\t<<JSON her>>",
                "UK\t3001\t3101",
                "UK\t3035\t3150",
                "UK\t3036\t3150",
                "UK\t3040\t3151",
                "UF\t30",
            ),
            reform(input),
        )
    }

    @Test
    fun `reform lister ikke eksisterende mål for sammenslåing`() {
        val input = input(
            emptyList(),
            listOf(kommune(31, 1, "Halden")),
            kommuneendring(31 to 2, listOf(31 to 1), sammenslaaing = true),
            kommuneendring(31 to 3, listOf(31 to 4)),
        ).copy(kommuner = listOf(kommune(31, 1, "Halden"), kommune(31, 4, "Ny")))

        assertEquals(
            listOf("DT\t2024-01-01", "NK\t3101\tHALDEN", "NK\t3104\tNY", "AN\t<<JSON her>>", "UK\t3102\t3101", "UK\t3103\t3104"),
            input.stedsnavnParametre(),
        )
        assertEquals(
            listOf("DT\t2024-01-01", "NK\t3104\tNY", "AN\t<<JSON her>>", "UK\t3102\t3101", "UK\t3103\t3104"),
            reform(input),
        )
    }
}
