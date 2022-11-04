package no.kartverket.komreg.experimental

interface EntitySourceFactory<C> {
    val name: String
    fun create(context: EntitySourceContext<C>): EntitySource<*>
}
