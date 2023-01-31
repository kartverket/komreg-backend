package no.kartverket.komreg.transformation
import no.kartverket.komreg.experimental.DatabaseEntity
import no.kartverket.komreg.experimental.Entity
import no.kartverket.komreg.experimental.VirtualEntity
import java.lang.System.Logger.Level.ERROR

sealed class Transform<T>(val entity: Entity<T>) {
    class Transformed<T>(data: Entity<T>, val transformation: TransformFunc<T>) : Transform<T>(data)
    class NoOp<T>(data: Entity<T>) : Transform<T>(data)
    //class Error<out T : EntityData>(data: T, val errors: List<String>) : Transform<T>(data)

    fun transform(trans: List<TransformFunc<T>>): Transform<T> = trans.fold(this) { acc, transformFunc ->
        acc.transform(transformFunc)
    }

    private fun transform(tf: TransformFunc<T>): Transform<T> = when (this) {
        is NoOp -> tf.map(this.entity)
        is Transformed -> {
            // TODO: Legg til higher kinded types i Kotlin ;-)
            val msgSupplier = { "Double t: ['${tf.description}', '${transformation.description}']" }
            val entity = when(entity) {
                is DatabaseEntity -> entity.copy(data = entity.data.log(ERROR, null,  msgSupplier))
                is VirtualEntity -> entity.copy(data = entity.data.log(ERROR, null,  msgSupplier))
            }
            Transformed(
                data = entity,
                transformation = this.transformation
            )
}
    }

    override fun toString(): String = "Transform(${this.entity}, " + when (this) {
        is NoOp -> "NoOp"
        is Transformed -> "Transformed: '${this.transformation.description}'@${Integer.toHexString(this.hashCode())}"
    } + ")"

    companion object {
        fun <T> noOp(t: Entity<T>): Transform<T> = NoOp(t)
    }
}

interface TransformFunc<T> {
    val description: String
    fun map(input: Entity<T>): Transform<T>
}
