package com.example.tv_caller_app.network

import com.example.tv_caller_app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json

/**
 * HTTP client for the .NET backend, which is the system of record for clinical
 * data such as appointments. Supabase remains the realtime event bus and the
 * source of identity — see [SupabaseClient].
 *
 * Shaped deliberately like [SupabaseClient]: a single object holding one shared
 * client, configured from local.properties via BuildConfig.
 *
 * Ktor rather than Retrofit because ktor-client-core/okhttp are already direct
 * dependencies (the Supabase SDK runs on them), so this adds no new libraries and
 * no second HTTP stack. The ContentNegotiation plugin is skipped for the same
 * reason — it would need two more artifacts — so responses are read as text and
 * decoded with [json] instead.
 */
object BackendApiClient {

    /** Includes a trailing slash; blank when not configured in local.properties. */
    val baseUrl: String = BuildConfig.BACKEND_BASE_URL

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /**
     * ignoreUnknownKeys is essential, not cosmetic: AppointmentDto carries about
     * 17 fields and this app models the handful it actually displays, so a strict
     * parser would throw on every response.
     */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }

        // Branch on status codes ourselves rather than having Ktor throw, so a 401
        // can trigger a token refresh and a 404 can become a distinct UI state.
        expectSuccess = false
    }

    /** Joins [path] onto the base URL without doubling or dropping the slash. */
    fun url(path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
