package com.ingenico.logontouch.di.modules

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Created by vigursky on 13.12.2017.
 */

@Module
class AppModule(var mApplication: Application) {

    @Provides
    @Singleton
    internal fun providesApplication(): Context {
        return mApplication
    }

    @Provides
    @Singleton
    internal fun provideConnectivityManager(): ConnectivityManager {
        return mApplication.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
}