package io.github.knoellp.roomscreen.roon

import io.github.knoellp.roomscreen.roon.moo.MooConnection
import io.github.knoellp.roomscreen.roon.moo.MooMessage
import io.github.knoellp.roomscreen.roon.moo.MooVerb
import io.github.knoellp.roomscreen.roon.moo.str
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Persists the authorization token per Core (`core_id`). */
interface TokenStore {
    fun get(coreId: String): String?
    fun put(coreId: String, token: String)
}

class MemoryTokenStore : TokenStore {
    private val tokens = HashMap<String, String>()
    @Synchronized override fun get(coreId: String) = tokens[coreId]
    @Synchronized override fun put(coreId: String, token: String) { tokens[coreId] = token }
}

data class ExtensionIdentity(
    val extensionId: String,
    val displayName: String,
    val displayVersion: String,
    val publisher: String,
    val email: String,
)

data class CoreInfo(val coreId: String, val displayName: String?, val displayVersion: String?)

class RegistryException(message: String) : Exception(message)

class Registry(
    private val connection: MooConnection,
    private val tokens: TokenStore,
    private val identity: ExtensionIdentity,
    private val logger: Logger = Logger.None,
) {
    /** Read-only handshake; does not register an extension. */
    suspend fun info(): CoreInfo {
        val reply = connection.call("$SERVICE/info")
        val body = reply.json() ?: throw RegistryException("info: no JSON body in ${reply.name}")
        val coreId = body.str("core_id") ?: throw RegistryException("info: no core_id")
        return CoreInfo(coreId, body.str("display_name"), body.str("display_version"))
    }

    /**
     * Registers and waits for `Registered`. `Pending`/`Registered` are accepted as CONTINUE or
     * COMPLETE. Without a valid token the user has to enable the extension in Roon, so the wait
     * is long ([authTimeoutMs], default 10 minutes). [onPending] fires when the Core reports Pending.
     * The new token is stored under the Core's `core_id` as soon as it arrives.
     */
    suspend fun register(
        coreId: String,
        authTimeoutMs: Long = 10 * 60_000L,
        onPending: () -> Unit = {},
    ): String {
        val stored = tokens.get(coreId)
        val request = buildJsonObject {
            put("extension_id", JsonPrimitive(identity.extensionId))
            put("display_name", JsonPrimitive(identity.displayName))
            put("display_version", JsonPrimitive(identity.displayVersion))
            put("publisher", JsonPrimitive(identity.publisher))
            put("email", JsonPrimitive(identity.email))
            put("required_services", JsonArray(listOf(TRANSPORT, IMAGE).map(::JsonPrimitive)))
            put("optional_services", JsonArray(emptyList()))
            put("provided_services", JsonArray(listOf(JsonPrimitive("com.roonlabs.ping:1"))))
            if (stored != null) put("token", JsonPrimitive(stored))
        }
        logger.info("registry=register_sent core_id=$coreId token_present=${stored != null}")

        val result = CompletableDeferred<String>()
        connection.send("$SERVICE/register", request) { msg ->
            when {
                msg == null -> result.completeExceptionally(RegistryException("connection closed while registering"))
                msg.name == "Registered" -> {
                    val token = msg.json()?.str("token")
                    if (token == null) {
                        result.completeExceptionally(RegistryException("Registered without token"))
                    } else {
                        tokens.put(coreId, token)
                        logger.info("registry=token_saved core_id=$coreId verb=${msg.verb}")
                        result.complete(token)
                    }
                }
                msg.name == "Pending" -> {
                    logger.info("registry=pending verb=${msg.verb}")
                    onPending()
                }
                msg.verb == MooVerb.COMPLETE ->
                    result.completeExceptionally(RegistryException("register failed: ${msg.name} ${msg.body?.decodeToString()}"))
                else -> logger.info("registry=register_progress verb=${msg.verb} name=${msg.name}")
            }
        }
        return withTimeout(authTimeoutMs) { result.await() }
    }

    companion object {
        const val SERVICE = "com.roonlabs.registry:1"
        const val TRANSPORT = "com.roonlabs.transport:2"
        const val IMAGE = "com.roonlabs.image:1"
    }
}
