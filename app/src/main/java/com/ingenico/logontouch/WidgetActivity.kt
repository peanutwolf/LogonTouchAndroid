package com.ingenico.logontouch

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.ingenico.logontouch.exception.UserCancelledException
import com.ingenico.logontouch.fragments.HostUnlockDialog
import com.ingenico.logontouch.service.HostBridgeService
import com.ingenico.logontouch.tools.AppLocalKeystore
import com.ingenico.logontouch.tools.HostAddressHolder
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.internal.Ref

class WidgetActivity : AppCompatActivity(), LogonTouchView, CoroutineScope {

    @Inject
    lateinit var mLocalKeystore: AppLocalKeystore

    @Inject
    lateinit var hostBridgeServiceRef: Ref.ObjectRef<HostBridgeService?>

    override val coroutineContext: CoroutineContext = SupervisorJob()

    override lateinit var hostUnlockDialog: HostUnlockDialog

    private lateinit var mKeyGuard: KeyguardManager

    private lateinit var logonTouchPresenter: LogonTouchPresenter

    private lateinit var sharedPreferences: SharedPreferences

    private var unlockDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        (application as LogonTouchApp).mSecurityComponent?.inject(this)

        mKeyGuard = this.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        logonTouchPresenter = LogonTouchPresenter(this, mLocalKeystore, this)

        val hostAddress = findHostAddress(sharedPreferences)

        if (hostAddress == null || !logonTouchPresenter.isDeviceBound()) {
            startActivity(Intent(this, MainActivity::class.java))
            return
        }

        hostUnlockDialog = HostUnlockDialog()
        hostUnlockDialog.setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_AppCompat_Full_Dialog)
        hostUnlockDialog.isCancelable = false

        launch {
            lateinit var hostBridgeService: HostBridgeService
            while (true){
                val hostBridgeServiceNullable = hostBridgeServiceRef.element
                if(hostBridgeServiceNullable != null){
                    hostBridgeService = hostBridgeServiceNullable
                    break
                }
                delay(100)
            }

            unlockDisposable = logonTouchPresenter.subscribeHostUnlock(hostAddress, hostBridgeService)
                .subscribe(
                    { Log.v(LOG_TAG, "Result of host unlock [$it]") },
                    {
                        Log.w(LOG_TAG, "Error of host unlock", it)
                        when (it) {

                            !is UserCancelledException -> {
                                hostUnlockDialog.isError = true
                                hostUnlockDialog.title = "Sorry, error :-("
                                hostUnlockDialog.message = it.message ?: "Unknown error occurred"
                                hostUnlockDialog.cancelListener = View.OnClickListener {
                                    finish()
                                }
                            }

                            else -> {
                                finish()
                            }
                        }
                    },
                    {
                        Log.d(LOG_TAG, "Complete host unlock")
                        finish()
                    }
                )
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        unlockDisposable?.dispose()
    }

    private var onUserCredentialAuth: Subject<Boolean>? = null
    override fun retryOnUserAuth(cause: Throwable): Flowable<Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return Flowable.error(cause)

        return when (cause) {
            is UserNotAuthenticatedException -> onUserAuthenticate()
            else -> Flowable.error(cause)
        }
    }

    override fun hostUnlockProgress(visible: Boolean) {
        if (visible) {
            showProgressDialog()
        } else {
            hostUnlockDialog.dismiss()
        }
    }

    private fun onUserAuthenticate(): Flowable<Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return Flowable.just(false)

        val intent = mKeyGuard.createConfirmDeviceCredentialIntent(null, null)
                     ?: return Flowable.just(false)

        startActivityForResult(intent, MainActivity.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS)
        return PublishSubject.create<Boolean>()
            .also { onUserCredentialAuth = it }
            .toFlowable(BackpressureStrategy.BUFFER)
    }

    private fun showProgressDialog() {
        val ft = fragmentManager.beginTransaction()
        val prev = fragmentManager.findFragmentByTag("HOST_UNLOCK_DIALOG")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        hostUnlockDialog.show(ft, "HOST_UNLOCK_DIALOG")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            MainActivity.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        onUserCredentialAuth?.onNext(true)
                    }
                    else -> onUserCredentialAuth?.onError(Exception("User not Authenticated"))
                }
            }
        }
    }

    private fun findHostAddress(preferences: SharedPreferences): HostAddressHolder? {
        if (!preferences.contains(getString(R.string.ip_preference)) || !preferences.contains(getString(R.string.port_preference)))
            return null

        return HostAddressHolder(
            preferences.getString(getString(R.string.ip_preference), getString(R.string.ip_default))!!,
            preferences.getString(getString(R.string.port_preference), getString(R.string.port_default))?.toInt()!!
        )
    }

    companion object {
        private val LOG_TAG = WidgetActivity::class.java.simpleName
    }
}