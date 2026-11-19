package io.github.kubatdavid.food

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/**
 * Talks to the pantry server on the Raspberry Pi (Caddy -> Apache -> api.php).
 *
 * The hostname stays food.force.local so TLS checks Caddy's certificate for that name
 * (trusted via res/xml/network_security_config.xml), but it is resolved straight to the
 * Pi's LAN address: ".local" names are often sent to mDNS by Android and never reach AdGuard.
 * Works on the home network and over WireGuard.
 */
class Api {
    companion object {
        const val HOST = "food.force.local"
        const val ADDRESS = "172.17.251.12"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname.equals(HOST, ignoreCase = true)) {
                    val bytes = ADDRESS.split('.').map { it.toInt().toByte() }.toByteArray()
                    listOf(InetAddress.getByAddress(HOST, bytes))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
        })
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun call(
        method: String,
        query: Map<String, String>,
        body: JsonObject? = null,
        path: String = "api.php",
    ): String = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder().scheme("https").host(HOST).addPathSegment(path).apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val requestBody = when (method) {
            "GET", "DELETE" -> null
            else -> (body ?: JsonObject(emptyMap())).toString().toRequestBody(JSON_TYPE)
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Pantry", "1")
            .header("Cache-Control", "no-cache")
            .method(method, requestBody)
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    val msg = runCatching { json.decodeFromString<ErrorResp>(text).error }.getOrNull()
                    throw ApiException(if (msg.isNullOrBlank()) "Server odpověděl ${resp.code}." else msg)
                }
                text
            }
        } catch (e: IOException) {
            throw ApiException("Server není dostupný. Jsi doma nebo na WireGuardu? (${e.message ?: e.javaClass.simpleName})")
        }
    }

    private suspend inline fun <reified T> get(r: String, extra: Map<String, String> = emptyMap()): T =
        json.decodeFromString<T>(call("GET", mapOf("r" to r) + extra))

    // Pantry
    suspend fun items(): List<PantryItem> = get<ItemsResp>("items").items
    suspend fun addItem(body: JsonObject): List<PantryItem> =
        json.decodeFromString<ItemsResp>(call("POST", mapOf("r" to "items"), body)).items
    suspend fun patchItem(id: String, body: JsonObject): List<PantryItem> =
        json.decodeFromString<ItemsResp>(call("PATCH", mapOf("r" to "items", "id" to id), body)).items
    suspend fun deleteItem(id: String): List<PantryItem> =
        json.decodeFromString<ItemsResp>(call("DELETE", mapOf("r" to "items", "id" to id))).items

    // Shopping list
    suspend fun shopping(): List<ShopItem> = get<ShopResp>("shopping").shopping
    internal suspend fun shopAdd(body: JsonObject): ShopResp =
        json.decodeFromString(call("POST", mapOf("r" to "shopping"), body))
    internal suspend fun shopPatch(id: String, body: JsonObject): ShopResp =
        json.decodeFromString(call("PATCH", mapOf("r" to "shopping", "id" to id), body))
    internal suspend fun shopDelete(id: String): ShopResp =
        json.decodeFromString(call("DELETE", mapOf("r" to "shopping", "id" to id)))
    internal suspend fun shopClearDone(): ShopResp =
        json.decodeFromString(call("DELETE", mapOf("r" to "shopping", "done" to "1")))
    internal suspend fun shopToPantry(): ShopResp =
        json.decodeFromString(call("POST", mapOf("r" to "shopping-to-pantry")))

    // Autocomplete data and recipes
    suspend fun names(): List<NameUsage> = get<NamesResp>("names").names
    suspend fun recipes(): List<RecipeRow> = get<RecipesResp>("recipes").recipes
    suspend fun recipe(slug: String): String = get<RecipeResp>("recipe", mapOf("slug" to slug)).text
    suspend fun catalog(): List<CatalogEntry> =
        json.decodeFromString<CatalogResp>(call("GET", emptyMap(), path = "catalog.json")).ingredients
}
