package com.ingenico.logontouch.di.components

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.ingenico.logontouch.MainActivity
import com.ingenico.logontouch.WidgetActivity
import com.ingenico.logontouch.di.modules.AppModule
import com.ingenico.logontouch.di.modules.SecurityModule
import com.ingenico.logontouch.di.modules.ServiceModule
import com.ingenico.logontouch.fragments.BindHostDialogFragment
import com.ingenico.logontouch.fragments.MainHeaderFragment
import dagger.Component
import javax.inject.Singleton

/**
 * Created by vigursky on 13.12.2017.
 */

@Singleton
@Component(modules = [AppModule::class, SecurityModule::class, ServiceModule::class])
interface SecurityComponent{
    fun inject(app: Application)

    fun inject(activity: MainActivity)
    fun inject(activity: WidgetActivity)

    fun inject(fragment: BindHostDialogFragment)
    fun inject(fragment: MainHeaderFragment)

    fun application(): Context
    fun connectivityManager(): ConnectivityManager
}