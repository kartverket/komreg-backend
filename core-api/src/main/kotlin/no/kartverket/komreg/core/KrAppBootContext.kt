package no.kartverket.komreg.core

import com.typesafe.config.Config

interface KrAppBootContext {
    val config: Config
}