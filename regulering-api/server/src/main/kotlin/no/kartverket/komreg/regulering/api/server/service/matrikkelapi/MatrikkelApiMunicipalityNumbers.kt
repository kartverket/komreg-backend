package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.reform.domain.type.MunicipalityNumber

interface MatrikkelApiMunicipalityNumbers {
    operator fun get(id: MunicipalityId): MunicipalityNumber
}
