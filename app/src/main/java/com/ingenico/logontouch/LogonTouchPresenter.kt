package com.ingenico.logontouch

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import com.ingenico.logontouch.exception.DeviceNotBindException
import com.ingenico.logontouch.exception.UserCancelledException
import com.ingenico.logontouch.service.HostBridgeService
import com.ingenico.logontouch.tools.AppLocalKeystore
import com.ingenico.logontouch.tools.HostAddressHolder
import com.ingenico.logontouch.tools.HostHTTPSClient
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.SingleSubject

class LogonTouchPresenter(
    private val context: Context,
    private val appKeystore: AppLocalKeystore,
    private val appView: LogonTouchView
) {

    private var keyStoreSubscription: Disposable? = null

    @SuppressLint("CheckResult")
    fun init(hostBridgeService: HostBridgeService) {
        if (isDeviceBound()) {
            hostBridgeService.readHostCertificates(appKeystore)
                .subscribeOn(Schedulers.io())
                .subscribe { _, _ ->
                    Log.d(LOG_TAG, "[init] Complete init certificate cache")
                }
        }
    }

    @SuppressLint("CheckResult")
    fun onCertificatesUpdated(hostBridgeService: HostBridgeService) {

        keyStoreSubscription?.dispose()

        hostBridgeService.clearCertificateCache()
        hostBridgeService.readHostCertificates(appKeystore)
            .subscribeOn(Schedulers.io())
            .subscribe { _, _ ->
                Log.d(LOG_TAG, "[onCertificatesUpdated] Complete init certificate cache")
            }
    }

    lateinit var hostUnlockInterruptible: SingleSubject<Boolean>
    fun subscribeHostUnlock(hostAddress: HostAddressHolder, hostBridgeService: HostBridgeService): Flowable<Boolean> {
        if (!isDeviceBound()) {
            return Flowable.error(DeviceNotBindException())
        }

        hostUnlockInterruptible = SingleSubject.create()

        return hostBridgeService.readHostCertificates(appKeystore)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                appView.hostUnlockDialog.isError = false
                appView.hostUnlockDialog.title = "Unlock ${hostAddress.ip}:${hostAddress.port}"
                appView.hostUnlockDialog.message = "Loading host secure certificates"
                appView.hostUnlockDialog.cancelListener = View.OnClickListener {
                    interruptHostUnlock()
                }
                appView.hostUnlockProgress(true)
            }
            .doOnSuccess {
                appView.hostUnlockDialog.message = "Connecting to secure host"
            }
            .observeOn(Schedulers.io())
            .map {
                val keyManagerStore = it.keyStore
                val trustManagerStore = it.trustStore

                val keyManagerPass = appKeystore.readWorkingSecretKey(AppLocalKeystore.CLIENT_CERT_PASSPHRASE_ALIAS)
                val hostClient = HostHTTPSClient(
                    hostBridgeService,
                    keyManagerStore,
                    keyManagerPass.toCharArray(),
                    trustManagerStore
                )

                val credentialKey = appKeystore.readWorkingSecretKey(AppLocalKeystore.CREDENTIAL_WORKING_KEY_ALIAS)
                val credentialIV = appKeystore.readWorkingSecretKey(AppLocalKeystore.CREDENTIAL_WORKING_IV_ALIAS)
                val res = hostClient.postCredentialKey(hostAddress, credentialKey, credentialIV)
                return@map res
            }
            .onErrorResumeNext {
                if (::hostUnlockInterruptible.isInitialized && hostUnlockInterruptible.hasThrowable())
                    Single.just(false)
                else
                    Single.error(it)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { hostUnlockInterruptible.onSuccess(it) }
            .mergeWith(hostUnlockInterruptible)
            .retryWhen { e ->
                e.flatMap {
                    Log.w(LOG_TAG, "Retry subscribeHostUnlock", it)
                    appView.retryOnUserAuth(it)
                }
            }
            .doOnComplete { appView.hostUnlockProgress(false) }

    }

    fun interruptHostUnlock() {
        hostUnlockInterruptible.onError(UserCancelledException())
    }

    fun isDeviceBound(): Boolean = appKeystore.keysLoaded() && context.clientCertsLoaded()

    fun AppLocalKeystore.keysLoaded(): Boolean {
        val b1 = masterKeyAvailable()
        val b2 = workingKeyAvailable(AppLocalKeystore.CREDENTIAL_WORKING_KEY_ALIAS)
        val b3 = workingKeyAvailable(AppLocalKeystore.CREDENTIAL_WORKING_IV_ALIAS)
        val b4 = workingKeyAvailable(AppLocalKeystore.CLIENT_CERT_PASSPHRASE_ALIAS)
        val b5 = workingKeyAvailable(AppLocalKeystore.SERVER_CERT_PASSPHRASE_ALIAS)

        return b1 and b2 and b3 and b4 and b5
    }

    fun Context.clientCertsLoaded(): Boolean {
        val b1 = AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME in applicationContext.fileList()
        val b2 = AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME in applicationContext.fileList()

        return b1 and b2
    }

    companion object {
        private val LOG_TAG = LogonTouchPresenter::class.java.simpleName
    }
}