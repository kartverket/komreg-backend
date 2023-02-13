package no.kartverket.komreg

import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UserServiceTest : Spek({

    describe("User ID") {
        val idGeneratorMock = mockk<IdGeneratorI> {
            every { getId() } returns "1"
        }
        val userservice = UserService(idGeneratorMock)
        it("should be random") {
            val newUser = userservice.createNewUser("Vegard", 32)
            assertThat(newUser.id, equalTo("1"))
            assertThat(userservice.getUserIdPlus1(newUser), equalTo("1_1"))
        }
    }
})
