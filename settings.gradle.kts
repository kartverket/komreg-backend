rootProject.name = "komreg-backend"

include(
    ":lib:core-api",
    ":lib:regulering-dto",
    ":lib:transformation",
    ":lib:regulering-mapping",
    ":lib:matrikkelapi-client",
    ":plugins:matrikkel-komreg",
    ":server",
    ":regulering-api:reform",
    ":regulering-api:server",
)


project(":regulering-api:server").name = "regulering-api-server"
project(":regulering-api:reform").name = "regulering-api-reform"
