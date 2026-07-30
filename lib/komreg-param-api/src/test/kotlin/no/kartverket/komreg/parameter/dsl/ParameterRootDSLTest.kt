package no.kartverket.komreg.parameter.dsl

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Kommunedata
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.parameter.Adjust
import no.kartverket.komreg.parameter.Move
import no.kartverket.komreg.parameter.data.FileLocation
import no.kartverket.komreg.parameter.data.HList
import no.kartverket.komreg.parameter.data.times
import no.kartverket.komreg.core.domain.Fylkesnummer as Fylke
import no.kartverket.komreg.core.domain.Kommunenummer.Lopenummer as Kommune
import no.kartverket.komreg.parameter.test.MatrikkelBehaviourSpec

class ParameterRootDSLTest : MatrikkelBehaviourSpec({
    val kommunedata1 = Kommunedata(
        navn = "",
        koordinatsystem = Koordinatsystem.UTM32,
        senterpunkt = Koordinat(0.0,0.0),
        nedsattKonsesjonsgrense = true,
        godkjenteGardsnumre = "",
        adresse = null,
        standardRekvirentOrgnummer = null,
        ikrafttredelsesdato = LocalDate(2000, 1, 1),
        kommunevapen = null
    )

    context("A set of parameters can be compiled to a lookup map or raise parameter error") {
        given("a merge of 6 kommuner from 4 fylker to 1 kommune") {
            val compiled = with(ParameterRootDSL()) {
                adjust(Fylke(1)) {
                    merge(
                        to = Kommune(1),
                        fromAll = hashSetOf(
                            HList * Fylke(10) * Kommune(1),
                            HList * Fylke(10) * Kommune(2),
                            HList * Fylke(10) * Kommune(3),
                            HList * Fylke(11) * Kommune(1),
                            HList * Fylke(12) * Kommune(1),
                            HList * Fylke(13) * Kommune(1),
                        ),
                        `as` = kommunedata1
                    )
                    move(Kommune(10), HList * Fylke(10) * Kommune(21))

                }
                compile().shouldBeRight { errs ->
                    "Compilation of parameters failed: $errs"
                }
            }

            then("all from mappings should be present when compiled") {
                compiled.parameterMap.keys shouldContainExactlyInAnyOrder hashSetOf(
                    HList * Fylke(1),
                    HList * Fylke(10),
                    HList * Fylke(11),
                    HList * Fylke(12),
                    HList * Fylke(13),
                    HList * Fylke(10) * Kommune(1),
                    HList * Fylke(10) * Kommune(2),
                    HList * Fylke(10) * Kommune(3),
                    HList * Fylke(11) * Kommune(1),
                    HList * Fylke(12) * Kommune(1),
                    HList * Fylke(13) * Kommune(1),
                )
            }
        }

    }
    context("A parameter root DSL should be able to group its parameters by type") {
        given("An empty root adjustment") {
            val grouped = with(ParameterRootDSL()) {
                groupByType()
            }
            `when`("grouped") {
                then("Should have no parameter groups") {
                    grouped.shouldBeEmpty()
                }
            }
        }

        given("An an root adjustment with adjustments of the same key type") {
            val adjustments = ParameterRootDSL()
            with(adjustments) {
                move(Fylke(1), HList * Fylke(11))
                adjust(Fylke(2)) {}
                move(Fylke(3), HList * Fylke(13))
            }
            `when`("grouped") {
                val grouped = adjustments.groupByType()
                then("Should have one parameter type") {
                    grouped shouldHaveSize 1
                }
                then("Should contain the same number of parameters, of the correct key type") {
                    val group = grouped.first().parameters
                    group shouldForAll { param ->
                        param.type shouldBe Fylke.Type
                    }
                    group shouldHaveSize 3
                }
            }
        }

        given("An root adjustment with multiple adjustements of the same subkey") {
            val adjustments = ParameterRootDSL()
            val fylke1 = HList * Fylke(1)
            val fylke2 = HList * Fylke(2)
            val (fileName, locationStart, locationEnd) = with(adjustments) {
                val fileLocation = FileLocation()
                adjust(Fylke(1)) {
                    move(Kommune(1), fylke2 * Kommune(11))
                }
                adjust(Fylke(1)) {
                    move(Kommune(2), fylke2 * Kommune(12))
                }
                Triple(
                    fileLocation.fileName,
                    fileLocation.lineNumber!!,
                    FileLocation().lineNumber!!
                )
            }
            val grouped = adjustments.buildMap().shouldBeRight {
                "Grouping failed: $it"
            }
            grouped.keys shouldContainExactly hashSetOf(fylke1)
            val fylke1Parameter = grouped[fylke1]!!
            fylke1Parameter.fileLocation shouldForAll {
                it.fileName shouldBeEqual fileName
                it.lineNumber shouldNotBeNull {
                    shouldBeIn(locationStart..locationEnd)
                }
            }
            fylke1Parameter.parameter.shouldBeTypeOf<Adjust<HList.Empty, *>> { adjust ->
                adjust.by.map { it.parameter } shouldContainExactlyInAnyOrder hashSetOf(
                    Move(Kommune(1), fylke2 * Kommune(11)),
                    Move(Kommune(2), fylke2 * Kommune(12))
                )

            }
        }
    }
})