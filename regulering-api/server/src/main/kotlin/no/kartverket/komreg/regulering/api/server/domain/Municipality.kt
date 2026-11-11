package no.kartverket.komreg.regulering.api.server.domain

import no.kartverket.komreg.regulering.reform.domain.type.MunicipalityNumber

data class Municipality(
    val id: MunicipalityId,
    val municipalityNumber: MunicipalityNumber
)
