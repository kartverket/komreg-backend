package no.kartverket.komreg

import java.util.UUID

class UserService(private val idGenerator: IdGeneratorI) {

    fun createNewUser(name: String, age: Int) =
        User(idGenerator.getId(), name, age)

    fun getUserIdPlus1(user: User) = "${user.id}_1"
}

interface IdGeneratorI {
    fun getId(): String
}

class IdGenerator : IdGeneratorI {
    override fun getId(): String = UUID.randomUUID().toString()
}

data class User(val id: String, val name: String, val age: Int)
