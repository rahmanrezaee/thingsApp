package com.example.thingsappandroid.di

import android.content.Context
import android.os.BatteryManager
import android.app.NotificationManager
import com.example.thingsappandroid.services.ConsumptionTrackerFactory
import com.example.thingsappandroid.services.ConsumptionTrackerFactoryImpl
import com.example.thingsappandroid.services.DeviceInfoApiFactory
import com.example.thingsappandroid.services.DeviceInfoApiFactoryImpl
import com.example.thingsappandroid.services.utils.BatteryNotificationHelper
import com.example.thingsappandroid.services.utils.ClimateStatusManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides dependencies used by BatteryService and other services.
 * Factories are bound to concrete implementations so kapt can resolve types.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindConsumptionTrackerFactory(impl: ConsumptionTrackerFactoryImpl): ConsumptionTrackerFactory

    @Binds
    @Singleton
    abstract fun bindDeviceInfoApiFactory(impl: DeviceInfoApiFactoryImpl): DeviceInfoApiFactory

    companion object {
        @Provides
        @Singleton
        fun provideBatteryManager(@ApplicationContext context: Context): BatteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        @Provides
        @Singleton
        fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        @Provides
        @Singleton
        fun provideBatteryNotificationHelper(
            @ApplicationContext context: Context,
            notificationManager: NotificationManager
        ): BatteryNotificationHelper = BatteryNotificationHelper(context, notificationManager)

        @Provides
        @Singleton
        fun provideClimateStatusManager(@ApplicationContext context: Context): ClimateStatusManager =
            ClimateStatusManager(context)
    }
}
