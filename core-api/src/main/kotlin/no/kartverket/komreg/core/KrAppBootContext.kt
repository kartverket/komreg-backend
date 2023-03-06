package no.kartverket.komreg.core

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretVersionName
import com.typesafe.config.Config

fun Config.getSecretOrString(prop: String): String {
    val value = this.getString(prop)

    return if (value.startsWith("sm://")) {
        value.toSecret() ?: throw IllegalStateException("Property $prop not found")
    } else {
        value
    }
}

private fun String.toSecret(): String? {
    val (_, _, projectId, secretId, versionId) = this.split("/")
    return getSecret(projectId, secretId, versionId)
}

fun getSecret(projectId: String, secretId: String, secretVersion: String): String? {
    return SecretManagerServiceClient.create().use { client ->
        val s = SecretVersionName.of(projectId, secretId, secretVersion)
        client.accessSecretVersion(s)
    }?.payload?.data?.toStringUtf8()
}

interface KrAppBootContext {
    val config: Config
}
