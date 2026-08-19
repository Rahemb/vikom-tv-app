package com.example.tv_caller_app.network

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.BackendNotConfiguredException
import com.example.tv_caller_app.datasource.ProfileNotLinkedException
import com.example.tv_caller_app.datasource.SessionExpiredException
import com.example.tv_caller_app.repository.AuthRepository
import io.github.jan.supabase.gotrue.auth
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

/**
 * One authenticated GET against the .NET backend.
 *
 * Every TV endpoint needs the same four things: a configured base URL, a Supabase
 * bearer token, a single retry after a 401, and the same mapping of status codes
 * onto the app's distinct failure states. Doing that once here means a repository
 * cannot accidentally handle an expired session differently from its neighbour.
 */
object BackendGet {

    private const val TAG = "BackendGet"

    /**
     * Returns the response body for [path], or throws the failure the UI should
     * render.
     *
     * @throws BackendNotConfiguredException backend.base.url is unset.
     * @throws SessionExpiredException no token, or still 401 after a refresh.
     * @throws ProfileNotLinkedException the backend has no patient for this profile.
     */
    suspend fun text(path: String, sessionManager: SessionManager): String =
        request(path, sessionManager, allowRetry = true)

    private suspend fun request(
        path: String,
        sessionManager: SessionManager,
        allowRetry: Boolean
    ): String {
        if (!BackendApiClient.isConfigured) {
            throw BackendNotConfiguredException()
        }

        val token = currentAccessToken(sessionManager) ?: throw SessionExpiredException()

        val response = BackendApiClient.client.get(BackendApiClient.url(path)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }

        return when (val code = response.status.value) {
            200 -> response.bodyAsText()

            401 -> {
                if (!allowRetry) throw SessionExpiredException()

                // The access token most likely expired between scheduled refreshes.
                // Reuse AuthRepository's refresh so there is only one implementation
                // of that logic, then retry exactly once — allowRetry = false on the
                // way back in is what prevents unbounded recursion.
                Log.w(TAG, "401 from backend — refreshing Supabase session and retrying once")

                AuthRepository.getInstance(sessionManager)
                    .refreshSession()
                    .getOrElse { throw SessionExpiredException() }

                request(path, sessionManager, allowRetry = false)
            }

            404 -> throw ProfileNotLinkedException()

            else -> throw Exception("Backend returned HTTP $code")
        }
    }

    /**
     * Prefers the in-memory Supabase session, which is the freshest source after a
     * refresh, and falls back to encrypted storage on a cold start before any
     * refresh has run.
     */
    private fun currentAccessToken(sessionManager: SessionManager): String? =
        SupabaseClient.client.auth.currentSessionOrNull()?.accessToken
            ?: sessionManager.getAccessToken()
}
