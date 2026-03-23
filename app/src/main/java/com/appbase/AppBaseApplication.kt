package com.appbase

import android.app.Application
import com.appbase.di.subscriptionModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Custom Application class that serves as the entry point for the app.
 * Responsible for initializing app-wide dependencies before any Activity starts.
 */
class AppBaseApplication : Application() {

    /**
     * Called when the application is starting, before any activity, service,
     * or receiver objects have been created.
     * 
     * Initializes the Koin dependency injection framework with:
     * - Android logging for debugging DI issues
     * - Application context for Android-specific dependencies
     * - The subscription module containing Retrofit, API services, repositories, and ViewModels
     */
    override fun onCreate() {
        super.onCreate()

        startKoin {
            // Enable Koin logging for debug builds
            androidLogger()
            // Provide application context to Koin for Android dependencies
            androidContext(this@AppBaseApplication)
            // Load all dependency modules (Retrofit, repositories, ViewModels)
            modules(subscriptionModule)
        }
    }
}
