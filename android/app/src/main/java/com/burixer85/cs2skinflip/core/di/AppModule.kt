package com.burixer85.cs2skinflip.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.burixer85.cs2skinflip.BuildConfig
import com.burixer85.cs2skinflip.core.data.local.AlertDao
import com.burixer85.cs2skinflip.core.data.local.AppDatabase
import com.burixer85.cs2skinflip.core.data.local.WatchlistDao
import com.burixer85.cs2skinflip.core.auth.AuthInterceptor
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.SteamApiService
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "cs2skinflip.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .build()

    // ─── Steam Web API ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("steam")
    fun provideSteamRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.steampowered.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

    @Provides
    @Singleton
    fun provideSteamApiService(@Named("steam") retrofit: Retrofit): SteamApiService =
        retrofit.create(SteamApiService::class.java)

    @Provides
    @Named("steam_api_key")
    fun provideSteamApiKey(): String = BuildConfig.STEAM_API_KEY

    // ─── CS2SkinFlip Backend ──────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendRetrofit(client: OkHttpClient, authInterceptor: AuthInterceptor): Retrofit {
        val clientWithAuth = client.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL.trimEnd('/') + "/")
            .client(clientWithAuth)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
    }

    @Provides
    @Singleton
    fun provideCS2BackendApiService(@Named("backend") retrofit: Retrofit): CS2BackendApiService =
        retrofit.create(CS2BackendApiService::class.java)
}
