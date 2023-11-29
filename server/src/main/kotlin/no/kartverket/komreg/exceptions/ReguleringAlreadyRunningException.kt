package no.kartverket.komreg.exceptions

class ReguleringAlreadyRunningException(id: String) :
    Exception("En kjøring for reguleringen med ID $id er allerede startet.")
