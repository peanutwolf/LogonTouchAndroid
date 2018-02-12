package com.ingenico.logontouch.di.modules

import android.content.Context
import com.ingenico.logontouch.tools.AppLocalKeystore
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Created by vigursky on 13.12.2017.
 */

@Module
class SecurityModule{
    @Provides
    @Singleton
    fun provideLocalKeystore(context: Context): AppLocalKeystore{
        return AppLocalKeystore(context)
    }
}