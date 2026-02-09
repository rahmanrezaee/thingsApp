package com.example.thingsappandroid.di

import android.content.Context
import com.example.thingsappandroid.data.local.AppDatabase
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.data.local.dao.ConsumptionDao
import com.example.thingsappandroid.data.repository.ThingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
        return PreferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideConsumptionDao(database: AppDatabase): ConsumptionDao {
        return database.consumptionDao()
    }

    @Provides
    @Singleton
    fun provideThingsRepository(
        preferenceManager: PreferenceManager,
        tokenManager: TokenManager
    ): ThingsRepository {
        return ThingsRepository( preferenceManager, tokenManager)
    }
}
