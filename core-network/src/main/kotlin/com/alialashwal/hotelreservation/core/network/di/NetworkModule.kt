package com.alialashwal.hotelreservation.core.network.di

import com.alialashwal.hotelreservation.core.network.BuildConfig
import com.alialashwal.hotelreservation.core.network.HotelRemoteDataSource
import com.alialashwal.hotelreservation.core.network.internal.LiteApiHotelRemoteDataSource
import com.alialashwal.hotelreservation.core.network.internal.LiteApiService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        // The catalogue returns far more per hotel than this app reads, and LiteAPI
        // adds fields without warning. Ignoring unknown keys is what stops a new field
        // from turning into a parse failure on a user's device.
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * The key never appears in a URL or in source. It is read from local.properties at
     * build time and attached here, so it stays out of the repository and out of logs
     * that record request lines.
     */
    @Provides
    @Singleton
    fun apiKeyInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("X-API-Key", BuildConfig.LITEAPI_KEY)
            .addHeader("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun okHttpClient(apiKeyInterceptor: Interceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(apiKeyInterceptor)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.LITEAPI_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun liteApiService(retrofit: Retrofit): LiteApiService = retrofit.create(LiteApiService::class.java)

    /** The catalogue call is slow enough that the default ten seconds trips on mobile data. */
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindings {

    @Binds
    @Singleton
    fun remoteDataSource(impl: LiteApiHotelRemoteDataSource): HotelRemoteDataSource
}
