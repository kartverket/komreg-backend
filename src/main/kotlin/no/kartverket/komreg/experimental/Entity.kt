package no.kartverket.komreg.experimental

sealed interface Entity<A> {
    val data: Validation<A>
}
