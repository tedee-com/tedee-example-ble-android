package com.tedee.flutter.api.service

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import com.tedee.flutter.PERSONAL_ACCESS_KEY

object ApiProvider {
  private const val BASE_URL = "https://api.tedee.com/"
  private const val AUTHORIZATION = "Authorization"
  private const val PERSONAL_KEY = "PersonalKey"

  private val loggingInterceptor: HttpLoggingInterceptor
    get() {
      val logging = HttpLoggingInterceptor()
      logging.level = HttpLoggingInterceptor.Level.BODY
      return logging
    }

  private val headerInterceptor: Interceptor
    get() {
      val header = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        builder.header(AUTHORIZATION, "$PERSONAL_KEY $PERSONAL_ACCESS_KEY")
        return@Interceptor chain.proceed(builder.build())
      }
      return header
    }

  private val retrofit: Retrofit by lazy {
    Retrofit.Builder()
      .baseUrl(BASE_URL)
      .client(createClient())
      .addConverterFactory(ScalarsConverterFactory.create())
      .addConverterFactory(GsonConverterFactory.create())
      .build()
  }

  private fun createClient(): OkHttpClient {
    return OkHttpClient.Builder()
      .apply {
        addInterceptor(headerInterceptor)
        addNetworkInterceptor(loggingInterceptor)
      }
      .build()
  }

  fun provideApi(): MobileApi = retrofit.create(MobileApi::class.java)

  fun provideGson() = Gson()
}
