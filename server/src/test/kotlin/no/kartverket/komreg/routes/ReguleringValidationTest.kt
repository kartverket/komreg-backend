package no.kartverket.komreg.routes

import io.kotest.core.spec.style.AnnotationSpec
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.validation.ErrorType
import no.kartverket.komreg.validation.ReguleringValidator
import kotlin.test.assertEquals

class ReguleringValidationTest : AnnotationSpec() {
    @Test
    fun `Fylkesdeling må inneholde transformasjoner for dens kommuner`() {
        val endringer =
            listOf(
                EndringDTO(
                    id = "endringId",
                    navn = "Fylkesdeling",
                    type = "fylke",
                    utgåendeFylker = emptyList(),
                    utgåendeKommuner = emptyList(),
                    nyeFylker = emptyList(),
                    nyeKommuner = emptyList(),
                    transformasjoner =
                    listOf(
                        FylkeTransformasjonDTO(
                            fylkesnummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("02", "03"),
                            ),
                        ),
                    ),
                ),
            )

        val regulering =
            Regulering(
                "regId",
                "regulering med feil",
                LocalDate(2024, 1, 1),
                endringer,
            )

        val errors = mutableMapOf("endringId" to listOf(ErrorType.FYLKESDELING_MANGLER_KOMMUNER))
        assertEquals(errors, ReguleringValidator.validateFylkesdeling(regulering))
    }
//    @Test
//    fun `sammenslåing`() {
//        val endringer =
//            listOf(
//                EndringDTO(
//                    id = "endringId",
//                    navn = "Fylkesdeling",
//                    type = "fylke",
//                    utgåendeFylker = emptyList(),
//                    utgåendeKommuner = emptyList(),
//                    nyeFylker = emptyList(),
//                    nyeKommuner = emptyList(),
//                    transformasjoner =
//                        listOf(
//                            KommuneTransformasjonDTO(
//                                fylkesnummer =
//                                    FraEnTilMangeDTO(
//                                        fra = "1000002",
//                                        til = listOf("1000002"),
//                                    ),
//                                kommuneløpenummer =
//                                    FraEnTilMangeDTO(
//                                        fra = "01",
//                                        til = listOf("90"),
//                                    ),
//                                sammenslaa = true
//                            ),
//                            KommuneTransformasjonDTO(
//                                fylkesnummer =
//                                    FraEnTilMangeDTO(
//                                        fra = "1000002",
//                                        til = listOf("1000002"),
//                                    ),
//                                kommuneløpenummer =
//                                    FraEnTilMangeDTO(
//                                        fra = "19",
//                                        til = listOf("90"),
//                                    ),
//                            ),
//                            KulturminneTransformasjonDTO(
//                                fylkesnummer =
//                                    FraTilDTO(
//                                        fra = "1000002",
//                                        til = "1000002",
//                                    ),
//                                kommuneløpenummer =
//                                    FraTilDTO(
//                                        fra = "01",
//                                        til = "90",
//                                    ),
//                                kulturminneId = FraTilDTO(
//                                    fra = "1001",
//                                    til = "1001",
//                                )
//                            )
//                        ),
//                ),
//            )
//
//        val regulering =
//            Regulering(
//                "regId",
//                "regulering sammenslaaing",
//                LocalDate(2026, 12, 12),
//                endringer,
//            )
//
//        val errors = mutableMapOf("endringId" to listOf(ErrorType.FYLKESDELING_KAN_IKKE_HA_SAMMENSLAAING))
//        assertEquals(errors, ReguleringValidator.validateFylkesdeling(regulering))
//    }
    @Test
    fun `Fylkesdeling kan ikke samtidig ha sammenslåing`() {
        val endringer =
            listOf(
                EndringDTO(
                    id = "endringId",
                    navn = "Fylkesdeling",
                    type = "fylke",
                    utgåendeFylker = emptyList(),
                    utgåendeKommuner = emptyList(),
                    nyeFylker = emptyList(),
                    nyeKommuner = emptyList(),
                    transformasjoner =
                        listOf(
                            FylkeTransformasjonDTO(
                                fylkesnummer =
                                    FraEnTilMangeDTO(
                                        fra = "01",
                                        til = listOf("02", "03"),
                                    ),
                                sammenslaa = true
                            ),
                            KommuneTransformasjonDTO(
                                fylkesnummer =
                                    FraEnTilMangeDTO(
                                        fra = "01",
                                        til = listOf("02"),
                                    ),
                                kommuneløpenummer =
                                    FraEnTilMangeDTO(
                                        fra = "01",
                                        til = listOf("02"),
                                    ),
                            ),
                            ),
                ),
            )

        val regulering =
            Regulering(
                "regId",
                "regulering med feil",
                LocalDate(2024, 1, 1),
                endringer,
            )

        val errors = mutableMapOf("endringId" to listOf(ErrorType.FYLKESDELING_KAN_IKKE_HA_SAMMENSLAAING))
        assertEquals(errors, ReguleringValidator.validateFylkesdeling(regulering))
    }

    @Test
    fun `Kommunedeling må inneholde transformasjoner for matrikkelenheter, kretser, teiger og veger`() {
        val endringer =
            listOf(
                EndringDTO(
                    id = "endringId",
                    navn = "Kommunedeling",
                    type = "kommune",
                    utgåendeFylker = emptyList(),
                    utgåendeKommuner = emptyList(),
                    nyeFylker = emptyList(),
                    nyeKommuner = emptyList(),
                    transformasjoner =
                    listOf(
                        KommuneTransformasjonDTO(
                            fylkesnummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                            kommuneløpenummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("02", "03"),
                            ),
                        ),
                    ),
                ),
            )

        val regulering =
            Regulering(
                "regId",
                "regulering med feil",
                LocalDate(2024, 1, 1),
                endringer,
            )

        val errors = mutableMapOf(
            "endringId" to
                    listOf(
                        ErrorType.KOMMUNEDELING_MANGLER_KRETSER,
                        ErrorType.KOMMUNEDELING_MANGLER_MATRIKKELENHETER,
                        ErrorType.KOMMUNEDELING_MANGLER_TEIGER,
                        ErrorType.KOMMUNEDELING_MANGLER_VEGER,
                    ),
        )

        assertEquals(errors, ReguleringValidator.validateKommunedeling(regulering))
    }

    @Test
    fun `Kommunedeling skal gi feilmelding om den mangler transformasjoner for krets`() {
        val endringer =
            listOf(
                EndringDTO(
                    id = "endringId",
                    navn = "Kommunedeling",
                    type = "kommune",
                    utgåendeFylker = emptyList(),
                    utgåendeKommuner = emptyList(),
                    nyeFylker = emptyList(),
                    nyeKommuner = emptyList(),
                    transformasjoner =
                    listOf(
                        KommuneTransformasjonDTO(
                            fylkesnummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                            kommuneløpenummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("02", "03"),
                            ),
                        ),
                        MatrikkelenhetTransformasjonDTO(
                            fylkesnummer =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                            kommuneløpenummer =
                            FraTilDTO(
                                fra = "01",
                                til = "02",
                            ),
                            gårdsnummer =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                        ),
                        TeigTransformasjonDTO(
                            fylkesnummer =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                            kommuneløpenummer =
                            FraTilDTO(
                                fra = "01",
                                til = "02",
                            ),
                            teigId =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                        ),
                        VegTransformasjonDTO(
                            fylkesnummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                            kommuneløpenummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("02"),
                            ),
                            adressekode =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                        ),
                    ),
                ),
            )

        val regulering =
            Regulering(
                "regId",
                "regulering med feil",
                LocalDate(2024, 1, 1),
                endringer,
            )

        val errors = mutableMapOf("endringId" to listOf(ErrorType.KOMMUNEDELING_MANGLER_KRETSER))
        assertEquals(errors, ReguleringValidator.validateKommunedeling(regulering))
    }

    @Test
    fun `Kommunedeling kan ikke samtidig ha sammenslåing`() {
        val endringer =
            listOf(
                EndringDTO(
                    id = "endringId",
                    navn = "Kommunedeling",
                    type = "kommune",
                    utgåendeFylker = emptyList(),
                    utgåendeKommuner = emptyList(),
                    nyeFylker = emptyList(),
                    nyeKommuner = emptyList(),
                    transformasjoner =
                    listOf(
                        KommuneTransformasjonDTO(
                            fylkesnummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                            kommuneløpenummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("02", "03"),
                            ),
                            sammenslaa = true,
                        ),
                        MatrikkelenhetTransformasjonDTO(
                            fylkesnummer =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                            kommuneløpenummer =
                            FraTilDTO(
                                fra = "01",
                                til = "02",
                            ),
                            gårdsnummer =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                        ),
                        TeigTransformasjonDTO(
                            fylkesnummer =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                            kommuneløpenummer =
                            FraTilDTO(
                                fra = "01",
                                til = "02",
                            ),
                            teigId =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                        ),
                        VegTransformasjonDTO(
                            fylkesnummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                            kommuneløpenummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("02"),
                            ),
                            adressekode =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                        ),
                        KretsTransformasjonDTO(
                            fylkesnummer =
                            FraTilDTO(
                                fra = "01",
                                til = "01",
                            ),
                            kommuneløpenummer =
                            FraTilDTO(
                                fra = "01",
                                til = "02",
                            ),
                            kretsnummer =
                                FraTilDTO(
                                    fra = "01",
                                    til = "02",
                                ),
                            kretstype =
                                FraTilDTO(
                                    fra = "1",
                                    til = "1",
                                ),
                        ),
                    ),
                ),
            )

        val regulering =
            Regulering(
                "regId",
                "regulering med feil",
                LocalDate(2024, 1, 1),
                endringer,
            )

        val errors = mutableMapOf("endringId" to listOf(ErrorType.KOMMUNEDELING_KAN_IKKE_HA_SAMMENSLAAING))
        assertEquals(errors, ReguleringValidator.validateKommunedeling(regulering))
    }

    @Test
    fun `Vegendring skal inneholde transformasjon av vegadresse, returnerer feil dersom dette mangler`() {
        val endringer =
            listOf(
                EndringDTO(
                    id = "endringId",
                    navn = "Vegendring mangler vegadresseendring",
                    type = "kommune",
                    utgåendeFylker = emptyList(),
                    utgåendeKommuner = emptyList(),
                    nyeFylker = emptyList(),
                    nyeKommuner = emptyList(),
                    transformasjoner =
                    listOf(
                        VegTransformasjonDTO(
                            fylkesnummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                            kommuneløpenummer =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("02", "03"),
                            ),
                            adressekode =
                            FraEnTilMangeDTO(
                                fra = "01",
                                til = listOf("01"),
                            ),
                        ),
                    ),
                ),
            )

        val regulering =
            Regulering(
                "regId",
                "regulering med feil",
                LocalDate(2024, 1, 1),
                endringer,
            )

        val error = mutableMapOf("endringId" to listOf(ErrorType.VEGDELING_MANGLER_VEGADRESSER))
        assertEquals(error, ReguleringValidator.validateVegdeling(regulering))
    }
}
