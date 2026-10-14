package no.kartverket.komreg.core

import com.typesafe.config.Config

fun Config.getSecretOrString(prop: String): String {
    return this.getString(prop)
}

interface KrAppBootContext {
    val config: Config
}
