package com.ingenico.logontouch.di.modules

import com.ingenico.logontouch.service.HostBridgeService
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import kotlin.jvm.internal.Ref

@Module
class ServiceModule(
    private val hostBridgeServiceRef: Ref.ObjectRef<HostBridgeService?>
){

    @Provides
    @Singleton
    fun provideHostBridgeService(): Ref.ObjectRef<HostBridgeService?>{
        return hostBridgeServiceRef
    }

}