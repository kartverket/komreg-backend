package no.kartverket.komreg.exceptions

class ReguleringAlreadyFinishedException(id: String) :
    Exception("Regulering med ID $id er allerede markert som ferdig. Kan ikke starte ny transformasjon.")
