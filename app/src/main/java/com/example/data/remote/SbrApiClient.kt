package com.example.data.remote

import com.example.data.local.SettingsDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class SbrApiClient(
    private val settingsDataStore: SettingsDataStore
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = runBlocking { settingsDataStore.getDecryptedAuthToken() }
            val original = chain.request()
            val req = if (!token.isNullOrBlank()) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()
            } else {
                original.newBuilder()
                    .header("Accept", "application/json")
                    .build()
            }
            chain.proceed(req)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedService: SbrVpnApiService? = null

    suspend fun getService(): Result<SbrVpnApiService> {
        val rawUrl = settingsDataStore.preferencesFlow.first().apiBaseUrl.trim()
        if (rawUrl.isBlank()) {
            return Result.failure(IllegalStateException("Backend API URL is not configured."))
        }
        val normalized = normalizeAndValidateUrl(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Backend URL must use HTTPS (or local loopback for dev testing)."))

        val existing = cachedService
        if (existing != null && cachedUrl == normalized) {
            return Result.success(existing)
        }

        return runCatching {
            val retrofit = Retrofit.Builder()
                .baseUrl("$normalized/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            val service = retrofit.create(SbrVpnApiService::class.java)
            cachedUrl = normalized
            cachedService = service
            service
        }
    }

    /**
     * Queries real external public IP over HTTPS.
     * Never returns a hardcoded or simulated IP address.
     */
    suspend fun resolveRealPublicIp(service: SbrVpnApiService?): String? = withContext(Dispatchers.IO) {
        if (service != null) {
            val backendIp = runCatching { service.getClientPublicIp().ip }.getOrNull()
            if (!backendIp.isNullOrBlank()) return@withContext backendIp
        }
        runCatching {
            val request = Request.Builder()
                .url("https://api.ipify.org")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()?.takeIf { it.isNotBlank() && it.length <= 45 }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /**
     * Measures real TCP socket connect RTT to a deployed server's reachable health/API port.
     * Returns null if unreachable rather than synthesizing fake latency.
     */
    suspend fun measureTcpLatencyMs(hostname: String, port: Int, timeoutMs: Int = 2500): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val startNs = System.nanoTime()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(hostname, port), timeoutMs)
                }
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
                elapsedMs.coerceAtLeast(1)
            }.getOrNull()
        }

    companion object {
        fun normalizeAndValidateUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val isLocalLoopback = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
            return if (scheme == "https" || (scheme == "http" && isLocalLoopback)) {
                trimmed
            } else {
                null
            }
        }
    }
}
