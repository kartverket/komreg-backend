rootProject.name = "komreg-backend"

include(
    ":lib:core-api",
    ":lib:regulering-dto",
    ":lib:transformation",
    ":lib:matrikkelapi-client",
    ":server",
    ":regulering-api:server",
)


project(":regulering-api:server").name = "regulering-api-server"
