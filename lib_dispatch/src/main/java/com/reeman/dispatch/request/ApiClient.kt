package com.reeman.dispatch.request

import com.reeman.commons.utils.SpManager
import com.reeman.dispatch.BuildConfig
import com.reeman.dispatch.constants.Constants
import com.reeman.dispatch.model.request.RoomLoginReq
import com.reeman.dispatch.request.service.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object ApiClient{

    private const val TAG = BuildConfig.DISPATCH_DIR
    var token: String? = null
    var roomName: String? = null
    var roomPwd: String? = null
    var host:String?=null
    var hostname:String?=null

    private val httpClient: OkHttpClient by lazy {
        buildHttpClient()
    }

    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(CustomTokenInterceptor())
            .addInterceptor(LoggingInterceptor())
            .addInterceptor(RetryInterceptor(listOf("/task/create"),20,2000L))

        try {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, TrustManagerFactory.getInstance("X509").trustManagers[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return builder.build()
    }

    fun getApiService(): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1")
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .client(httpClient)
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private class CustomTokenInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            val sp = SpManager.getInstance()

            if (!request.url().toString().endsWith("login")) {
                if (token.isNullOrEmpty()) {
                    token = sp.getString(Constants.DISPATCH_TOKEN, "")
                }
                request = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }

            var response = chain.proceed(request)
            if (response.code() == 401 && !request.url().toString().endsWith("login") && !host.isNullOrBlank() && !hostname.isNullOrBlank()) {
                if (!roomName.isNullOrEmpty() && !roomPwd.isNullOrEmpty()) {
                    Timber.tag(TAG).w("Token expired: $token")
                    runBlocking {
                        val mResponse = getApiService().loginRoomSync(Urls.getLoginUrl(host!!), RoomLoginReq(roomName!!, roomPwd!!, hostname!!, 1))
                        if (mResponse.isSuccessful){
                            mResponse.body()?.data?.get("token")?.let {
                                token = it
                                sp.edit().putString(Constants.DISPATCH_TOKEN, token).apply()
                                response.close()
                                request = request.newBuilder()
                                    .header("Authorization", "Bearer $token")
                                    .build()

                                response = chain.proceed(request)
                            }
                        }
                    }
                }
            }
            return response
        }
    }



    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            Timber.tag(TAG).w("Request URL: ${request.url()}")
            Timber.tag(TAG).w("Request Method: ${request.method()}")
//            Timber.tag(TAG).v("Request Headers: ${request.headers()}")

            request.body()?.let {
                val buffer = Buffer()
                it.writeTo(buffer)
                Timber.tag(TAG).w("Request Body: ${buffer.readUtf8()}")
            }

            val response = chain.proceed(request)

            Timber.tag(TAG).w("Response Code: ${response.code()}")
            Timber.tag(TAG).v("Response Headers: ${response.headers()}")

            response.body()?.let { responseBody ->
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer
                Timber.tag(TAG).w("Response Body: ${buffer.clone().readUtf8()}")
            }

            return response
        }
    }

    class RetryInterceptor(
        private val urlSuffixes: List<String>,
        private val maxRetryCount: Int = 3,
        private val retryDelayMillis: Long = 1000L
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var response: Response? = null
            var retryCount = 0

            val urlMatches = urlSuffixes.any { request.url().toString().endsWith(it) }
            if (!urlMatches)return chain.proceed(request)

            while (retryCount < maxRetryCount) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful || response.code() != 500) {
                        return response
                    }
                } catch (e: IOException) {
                    if (retryCount >= maxRetryCount - 1) {
                        Timber.tag(TAG).e("Request failed after $maxRetryCount retries: ${e.message}")
                        throw e
                    } else {
                        Timber.tag(TAG).w("Retrying... (${retryCount + 1}/$maxRetryCount)")
                    }
                }

                retryCount++
                Thread.sleep(retryDelayMillis)
            }

            return response ?: throw IOException("Failed to get response after $maxRetryCount retries.")
        }
    }

}
