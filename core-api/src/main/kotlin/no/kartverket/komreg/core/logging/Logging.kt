package no.kartverket.komreg.core.logging

import org.slf4j.Marker
import org.slf4j.helpers.BasicMarkerFactory

private val basicMarkerFactory = BasicMarkerFactory()

val FAG: Marker = basicMarkerFactory.getMarker("fag")