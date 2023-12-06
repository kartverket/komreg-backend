package no.kartverket.komreg.exceptions

class KjoringAlreadyFinishedException(id: String) :
    Exception("Kjoring med ID $id er allerede markert som ferdig. Kan ikke starte ny transformasjon.")
