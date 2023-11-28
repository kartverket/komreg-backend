package no.kartverket.komreg.exceptions

class ReguleringAlreadyRunningException(id: String) :
    Exception("Regulering med ID $id er allerede startet. Kan ikke starte ny transformasjon.")
