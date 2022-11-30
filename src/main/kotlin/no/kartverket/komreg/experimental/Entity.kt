package no.kartverket.komreg.experimental

import no.kartverket.komreg.domain.EntityData
import java.lang.System.Logger.Level
import java.lang.UnsupportedOperationException
import java.util.UUID
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf


interface Embeddable<out A : EntityData> {
    val id: Id<@UnsafeVariance A>

}

sealed interface Entity<out A : EntityData> : Embeddable<A> {
    val data: Validation<A>

    fun <B> fold(ifInvalid: () -> B, ifValid: (A) -> B): B {
        return data.fold({_, _ -> ifInvalid() }){ _, a ->
            ifValid(a)
        }
    }
}

fun <FA : Entity<A>, A : EntityData> FA.transform(f: (A) -> A): FA {
    return when (this) {
        is DatabaseEntity<*> -> (this as DatabaseEntity<A>).copy(data = data.map(f))
        is VirtualEntity<*> -> (this as VirtualEntity<A>).copy(data = data.map(f))
        else -> throw UnsupportedOperationException()
    } as FA
}

sealed interface SourceEntity<out A : EntityData> : Entity<A> {
    companion object {
        inline operator fun <reified A : EntityData> invoke(id: SourceId<A>, data: Validation<A>) : DatabaseEntity<A> = DatabaseEntity(id, data)
        inline operator fun <reified A : EntityData> invoke(id: GeneratedId<A>, data: Validation<A>) : VirtualEntity<A> = VirtualEntity(id, data)
    }
}

data class DatabaseEntity<A : EntityData>(
    override val id: SourceId<A>,
    override val data: Validation<A>
) : SourceEntity<A>

data class VirtualEntity<A : EntityData>(
    override val id: GeneratedId<A>,
    override val data: Validation<A>
) : SourceEntity<A>


sealed interface Id<A : EntityData> {
    val dataClass: Class<A>
}

data class SourceId<A : EntityData>(override val dataClass: Class<A>, val value: Any) : Id<A> {
    companion object {
        inline operator fun <reified A : EntityData> invoke(value: Any): SourceId<A> {
            val javaClass = A::class.java
            if (javaClass != typeOf<A>().javaType) {
                TODO("Parameterized types are not supported") // burde være lov så lenge parameteret
                // er wildcard eller lower bound for klassens argumenter
            }
            return SourceId(javaClass, value)
        }
    }
}

data class GeneratedId<A : EntityData>(override val dataClass: Class<A>, val uuid: UUID = UUID.randomUUID()) : Id<A> {
    companion object {
        inline operator fun <reified A : EntityData> invoke(uuid: UUID = UUID.randomUUID()): GeneratedId<A> {
            val javaClass = A::class.java
            if (javaClass != typeOf<A>().javaType) {
                TODO("Parameterized types are not supported") // burde være lov så lenge parameteret
                // er wildcard eller lower bound for klassens argumenter
            }
            return GeneratedId(javaClass, uuid)
        }
    }
}

inline fun <reified FA : Entity<A>, reified A : EntityData> FA.log(level: Level, msg: () -> String): FA {
    val data1 = data.log(level, null, msg())
    val id1 = this.id
    return when(this) {
        is DatabaseEntity<*> -> DatabaseEntity(id1 as SourceId<A>, data1)
        is VirtualEntity<*> -> VirtualEntity(id1 as GeneratedId<A>, data1)
        else -> throw UnsupportedOperationException()
    } as FA
}