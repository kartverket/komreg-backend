@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package no.kartverket.komreg.core

@JvmName("p1_component1")
inline operator fun <R> Product.Just<R>.component1(): R = this.elems[0] as R

@JvmName("p2_component1")
inline operator fun <R> And<R, *>.component1(): R = this.elems[0] as R

@JvmName("p2_component2")
inline operator fun <R> And<*, R>.component2(): R = this.elems[1] as R

@JvmName("p3_component1")
inline operator fun <R> And<And<R, *>, *>.component1(): R = this.elems[0] as R

@JvmName("p3_component2")
inline operator fun <R> And<And<*, R>, *>.component2(): R = this.elems[1] as R

@JvmName("p3_component3")
inline operator fun <R> And<And<*, *>, R>.component3(): R = this.elems[2] as R

@JvmName("p4_component1")
inline operator fun <R> And<And<And<R, *>, *>, *>.component1(): R = this.elems[0] as R

@JvmName("p4_component2")
inline operator fun <R> And<And<And<*, R>, *>, *>.component2(): R = this.elems[1] as R

@JvmName("p4_component3")
inline operator fun <R> And<And<And<*, *>, R>, *>.component3(): R = this.elems[2] as R

@JvmName("p4_component4")
inline operator fun <R> And<And<And<*, *>, *>, R>.component4(): R = this.elems[3] as R

@JvmName("p5_component1")
inline operator fun <R> And<And<And<And<R, *>, *>, *>, *>.component1(): R = this.elems[0] as R

@JvmName("p5_component2")
inline operator fun <R> And<And<And<And<*, R>, *>, *>, *>.component2(): R = this.elems[1] as R

@JvmName("p5_component3")
inline operator fun <R> And<And<And<And<*, *>, R>, *>, *>.component3(): R = this.elems[2] as R

@JvmName("p5_component4")
inline operator fun <R> And<And<And<And<*, *>, *>, R>, *>.component4(): R = this.elems[3] as R

@JvmName("p5_component5")
inline operator fun <R> And<And<And<And<*, *>, *>, *>, R>.component5(): R = this.elems[4] as R
