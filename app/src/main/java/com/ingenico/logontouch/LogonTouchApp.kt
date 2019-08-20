package com.ingenico.logontouch

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.ingenico.logontouch.di.components.DaggerSecurityComponent
import com.ingenico.logontouch.di.components.SecurityComponent
import com.ingenico.logontouch.di.modules.AppModule
import com.ingenico.logontouch.di.modules.SecurityModule
import com.ingenico.logontouch.di.modules.ServiceModule
import com.ingenico.logontouch.service.HostBridgeService
import kotlin.jvm.internal.Ref

class LogonTouchApp: Application() {

    var mSecurityComponent: SecurityComponent? = null
    var hostBridgeServiceRef: Ref.ObjectRef<HostBridgeService?> = Ref.ObjectRef()

    override fun onCreate() {
        super.onCreate()

        with(Intent(this, HostBridgeService::class.java)) {
            this@LogonTouchApp.bindService(this, mHostBridgeServiceConnection, Context.BIND_AUTO_CREATE)
        }

        mSecurityComponent = DaggerSecurityComponent.builder()
                .appModule(AppModule(this))
                .securityModule(SecurityModule())
                .serviceModule(ServiceModule(hostBridgeServiceRef))
                .build()
    }

    private val mHostBridgeServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(LOG_TAG, "HostBridgeService disconnected")
            hostBridgeServiceRef.element = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            hostBridgeServiceRef.element = (service as HostBridgeService.HostBridgeBinder).getService()
        }
    }

    companion object {

        private val LOG_TAG = LogonTouchApp::class.java.simpleName

    }
}