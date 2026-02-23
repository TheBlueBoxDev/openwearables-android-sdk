package com.openwearables.healthsdk.services

import com.openwearables.healthsdk.managers.NetworkConnectionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.IOException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ConnectivityInterceptor: Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return if (NetworkConnectionManager.shared.isConnected) {
            chain.proceed(chain.request())
        } else {
            throw IOException("No internet connection")
        }
    }
}

class NetworkBaseService(
    val apiBaseUrl: String
) {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .apply {
            addInterceptor(ConnectivityInterceptor())
            addInterceptor(logging)
        }
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> create(clazz: Class<T>): T {
        return retrofit.create(clazz)
    }
}