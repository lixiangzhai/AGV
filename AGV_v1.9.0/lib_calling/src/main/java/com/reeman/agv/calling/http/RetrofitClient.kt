package com.reeman.agv.calling.http

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import retrofit2.Retrofit
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val MAX_UNUSED_TIME = 30 * 60 * 1000L
    private val retrofitCache = ConcurrentHashMap<String, CachedRetrofit>()

    data class CachedRetrofit(val retrofit: Retrofit, var lastUsedTime: Long)

    private fun provideOkHttpClient(clientData: HttpClientData): OkHttpClient {

        return OkHttpClient.Builder()
            .apply {
                connectTimeout(clientData.connectTimeout, TimeUnit.SECONDS)
                readTimeout(clientData.readTimeout, TimeUnit.SECONDS)
                addInterceptor(LoggingInterceptor(clientData.tag))
                if (clientData.headers.isNotEmpty()) {
                    addInterceptor { chain ->
                        val originalRequest = chain.request()
                        val requestBuilder = originalRequest.newBuilder()
                        clientData.headers.forEach { (key, value) ->
                            requestBuilder.addHeader(key, value)
                        }
                        val request = requestBuilder.build()
                        chain.proceed(request)
                    }
                }
                if (clientData.maxRetries > 0 && clientData.retryDelaySeconds > 0) {
                    addInterceptor(RetryInterceptor(clientData.tag, clientData.maxRetries, clientData.retryDelaySeconds))
                }
            }
            .build()
    }

    private class RetryInterceptor(
        val tag: String,
        val maxRetries: Int = 3,
        val retryDelaySeconds: Int = 3
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response: Response? = null
            var exception: IOException? = null
            for (i in 0 until maxRetries) {
                try {
                    response = chain.proceed(request)
                    break
                } catch (e: IOException) {
                    exception = e
                    Timber.tag(tag).w("请求超时,${i * retryDelaySeconds}s后重试")
                    try {
                        Thread.sleep(i * retryDelaySeconds * 1000L)
                    } catch (e: Exception) {
                        break
                    }
                }
            }
            if (response == null) {
                throw exception!!
            }
            return response
        }
    }

    private class LoggingInterceptor(val tag: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            Timber.tag(tag).w("Request URL: %s", request.url())
            Timber.tag(tag).w("Request Method: %s", request.method())

            request.body()?.let {
                val buffer = Buffer()
                it.writeTo(buffer)
                Timber.tag(tag).w("Request Body: %s", buffer.readUtf8())
            }
            val response = chain.proceed(request)
            Timber.tag(tag).w("Response Code: %s", response.code())
            val responseBody = response.body()
            if (responseBody != null) {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer()
                Timber.tag(tag).w("Response Body: %s", buffer.clone().readUtf8())
            }

            return response
        }
    }

    fun <T> createService(clientData: HttpClientData, serviceClass: Class<T>): T {
        val cacheKey = "${clientData.baseUrl}-${clientData.connectTimeout}-${clientData.readTimeout}-${clientData.headers.hashCode()}"
        val currentTime = System.currentTimeMillis()
        val cachedRetrofit = retrofitCache[cacheKey]

        val retrofit = if (cachedRetrofit != null) {
            cachedRetrofit.lastUsedTime = currentTime
            cachedRetrofit.retrofit
        } else {
            val client = provideOkHttpClient(clientData = clientData)
            val retrofit = Retrofit.Builder()
                .baseUrl(clientData.baseUrl)
                .client(client)
                .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory(MediaType.get("application/json")))
                .build()
            Timber.tag(clientData.tag).w("create retrofit client: $cacheKey")
            retrofitCache[cacheKey] = CachedRetrofit(retrofit, currentTime)
            retrofit
        }
        cleanupCache(clientData.tag)
        return retrofit.create(serviceClass)
    }

    private fun cleanupCache( tag:String) {
        val currentTime = System.currentTimeMillis()
        val iterator = retrofitCache.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cachedRetrofit = entry.value
            if (currentTime - cachedRetrofit.lastUsedTime > MAX_UNUSED_TIME) {
                Timber.tag(tag).w("retrofitClient: ${entry.key} 长时间未使用,移除")
                iterator.remove()
            }
        }
    }
}
