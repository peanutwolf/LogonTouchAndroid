package com.ingenico.logontouch

import android.app.Application
import com.ingenico.logontouch.di.components.DaggerSecurityComponent
import com.ingenico.logontouch.di.components.SecurityComponent
import com.ingenico.logontouch.di.modules.AppModule
import com.ingenico.logontouch.di.modules.SecurityModule

/**
 * Created by vigursky on 13.12.2017.
 */
class LogonTouchApp: Application() {

    var mSecurityComponent: SecurityComponent? = null

    override fun onCreate() {
        super.onCreate()

        mSecurityComponent = DaggerSecurityComponent.builder()
                .appModule(AppModule(this))
                .securityModule(SecurityModule())
                .build()
    }
}