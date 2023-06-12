package no.kartverket.komreg.transformation

import com.typesafe.config.Config
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val featureToggleLogger: Logger = LoggerFactory.getLogger({}::class.java)

suspend fun Config.featureToggle(
    feature: String,
    enabled: suspend () -> Unit,
    disabled: suspend () -> Unit = {},
) {
    val isFeatureEnabled = this.getBoolean(feature)
    featureToggleLogger.debug("FeatureToggle invoked: $feature = $isFeatureEnabled")
    when (isFeatureEnabled) {
        true -> enabled()
        false -> disabled()
    }
}
