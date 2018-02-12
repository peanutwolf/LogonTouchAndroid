package com.ingenico.logontouch.di.components

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.ingenico.logontouch.MainActivity
import com.ingenico.logontouch.di.modules.AppModule
import com.ingenico.logontouch.di.modules.SecurityModule
import com.ingenico.logontouch.fragments.BindHostDialogFragment
import com.ingenico.logontouch.fragments.MainHeaderFragment
import dagger.Component
import javax.inject.Singleton

/**
 * Created by vigursky on 13.12.2017.
 */

@Singleton
@Component(modules = arrayOf(AppModule::class, SecurityModule::class))
interface SecurityComponent{
    fun inject(app: Application)

    fun inject(activity: MainActivity)
    fun inject(fragment: BindHostDialogFragment)
    fun inject(fragment: MainHeaderFragment)

    fun application(): Context
    fun connectivityManager(): ConnectivityManager
}