package no.kartverket.komreg

import com.typesafe.config.Config
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val featureToggleLogger: Logger = LoggerFactory.getLogger({}::class.java)

fun Config.featureToggle(
    feature: String,
): Boolean {
    val isFeatureEnabled = this.getBoolean(feature)
    featureToggleLogger.debug("FeatureToggle invoked: $feature = $isFeatureEnabled")
    return isFeatureEnabled
}
