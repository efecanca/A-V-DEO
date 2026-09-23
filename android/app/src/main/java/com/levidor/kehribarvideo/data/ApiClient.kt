package com.levidor.kehribarvideo.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit istemcisini sunucu adresine göre yeniden oluşturur.
 * Sunucu adresi Ayarlar ekranından değiştirildiğinde çağrılır.
 */
object ApiClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var currentBaseUrl: String? = null

    fun getService(rawBaseUrl: String): ApiService {
        val baseUrl = ServerUrl.normalize(rawBaseUrl)
        if (retrofit == null || currentBaseUrl != baseUrl) {
            synchronized(this) {
                if (retrofit == null || currentBaseUrl != baseUrl) {
                    val logging = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .writeTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor { chain ->
                            val request = chain.request().newBuilder()
                                // Ngrok ücretsiz tünelinin HTML uyarı sayfası yerine
                                // FastAPI cevabını doğrudan iletmesini sağlar.
                                .header("ngrok-skip-browser-warning", "true")
                                .header("Accept", "application/json")
                                .build()
                            chain.proceed(request)
                        }
                        .addInterceptor(logging)
                        .build()

                    retrofit = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    currentBaseUrl = baseUrl
                }
            }
        }
        return retrofit!!.create(ApiService::class.java)
    }
}
