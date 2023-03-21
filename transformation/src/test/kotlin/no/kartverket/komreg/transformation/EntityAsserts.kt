package no.kartverket.komreg.transformation

import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.kartverket.komreg.integration.spi.Entity

infix fun Entity.shouldHaveId(expected: String) {
    withClue(Entity::id.name) { id shouldBe expected }
}

fun Entity.shouldHaveIdent(vararg parts: Any) {
    val partMap: Map<Any, *> = parts.associateBy { it::class }
    withClue(Entity::ident.name) {
        (ident.shouldNotBeNull() as Map<Any, *>) shouldContainAll partMap
    }
}
