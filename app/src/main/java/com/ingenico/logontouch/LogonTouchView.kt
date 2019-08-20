package com.ingenico.logontouch

import com.ingenico.logontouch.fragments.HostUnlockDialog
import io.reactivex.Flowable

interface LogonTouchView{

    val hostUnlockDialog: HostUnlockDialog

    fun retryOnUserAuth(cause: Throwable): Flowable<Boolean>

    fun hostUnlockProgress(visible: Boolean)

}