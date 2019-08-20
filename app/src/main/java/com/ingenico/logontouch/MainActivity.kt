package com.ingenico.logontouch

import android.Manifest
import android.app.Activity
import android.app.Fragment
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.security.keystore.UserNotAuthenticatedException
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.dlazaro66.qrcodereaderview.QRCodeReaderView
import com.ingenico.logontouch.dict.StatusState
import com.ingenico.logontouch.exception.UserCancelledException
import com.ingenico.logontouch.fragments.BindHostDialogFragment
import com.ingenico.logontouch.fragments.HostUnlockDialog
import com.ingenico.logontouch.fragments.IdleStatusFragment
import com.ingenico.logontouch.fragments.SecurityLockFragment
import com.ingenico.logontouch.service.HostBridgeService
import com.ingenico.logontouch.tools.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.internal.Ref

/**
 * Copyright (c) 2018 All Rights Reserved, Ingenico LLC.
 *
 * Created by vigursky
 * Date: 15.11.2017
 */

class MainActivity : AppCompatActivity(), IdleStatusFragment.IdleStatusState, LogonTouchView, CoroutineScope {

    private lateinit var mKeyGuard: KeyguardManager

    private lateinit var logonTouchPresenter: LogonTouchPresenter

    override lateinit var hostUnlockDialog: HostUnlockDialog

    override val coroutineContext: CoroutineContext = SupervisorJob()

    @Inject
    lateinit var mLocalKeystore: AppLocalKeystore

    @Inject
    lateinit var hostBridgeServiceRef: Ref.ObjectRef<HostBridgeService?>

    private var currentIdleStatusFragment: IdleStatusFragment? = null
    var mCurrentIdleStatusState: StatusState = StatusState.APPLICATION_LOADING
        set(value) {
            field = value
            currentIdleStatusFragment?.setState(value)
        }

    private val mActivityFragmentsMap: HashMap<String, Fragment> = HashMap()

    init {
        mActivityFragmentsMap[SECURITY_LOCK_FRAGMENT] = SecurityLockFragment()
        mActivityFragmentsMap[IDLE_STATUS_FRAGMENT] = IdleStatusFragment()
    }

    override fun onIdleStatusFragmentLoaded(statusFragment: IdleStatusFragment) {
        currentIdleStatusFragment = statusFragment
        currentIdleStatusFragment?.setState(mCurrentIdleStatusState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        migrateSharedPreferences()

        setContentView(R.layout.activity_main)
        (application as LogonTouchApp).mSecurityComponent?.inject(this)
        mKeyGuard = this.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        logonTouchPresenter = LogonTouchPresenter(this, mLocalKeystore, this)

        hostUnlockDialog = HostUnlockDialog().also {
            it.setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_AppCompat_Full_Dialog)
            it.isCancelable = false
        }
    }

    override fun onStart() {
        super.onStart()

        launch {
            while (true) {
                val hostBridgeServiceNullable = hostBridgeServiceRef.element
                if (hostBridgeServiceNullable != null) {
                    logonTouchPresenter.init(hostBridgeServiceNullable)
                    break
                }
                delay(100)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        val headerFragmentView = findViewById<View>(R.id.headerFragment)
        val locked = mKeyGuard.isKeyguardLocked
        val secure = mKeyGuard.isKeyguardSecure
        when {
            secure.not() -> {
                setViewAndChildrenEnabled(headerFragmentView, enabled = false)
                replaceBodyFragmentWith(SECURITY_LOCK_FRAGMENT)
            }
            locked -> {
                setViewAndChildrenEnabled(headerFragmentView, enabled = false)
                startActivity(Intent("com.android.credentials.UNLOCK"))
            }
            else -> {
                replaceBodyFragmentWith(IDLE_STATUS_FRAGMENT)
                if (mLocalKeystore.masterKeyAvailable().not()) {
                    mLocalKeystore.generateMasterKey()
                }
                mCurrentIdleStatusState = when (logonTouchPresenter.isDeviceBound()) {
                    true -> StatusState.DEVICE_BIND
                    false -> StatusState.DEVICE_NOT_BIND
                }
                setViewAndChildrenEnabled(headerFragmentView, enabled = true)
            }
        }
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
        if (visible) showProgressDialog()
        else dismissProgressDialog()
    }

    private fun onUserAuthenticate(): Flowable<Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return Flowable.just(false)

        val intent = mKeyGuard.createConfirmDeviceCredentialIntent(null, null)
                     ?: return Flowable.just(false)

        startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS)
        return PublishSubject.create<Boolean>()
            .also { onUserCredentialAuth = it }
            .toFlowable(BackpressureStrategy.BUFFER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        onUserCredentialAuth?.onNext(true)
                    }
                    else -> onUserCredentialAuth?.onError(Exception("User not Authenticated"))
                }
            }
        }
    }

    private val onCameraPermissionGranted = PublishSubject.create<Boolean>()
    fun requestCameraPermission(): Observable<Boolean> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            return Observable.just(true)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CAMERA)
        return onCameraPermissionGranted
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CAMERA -> {
                if (permissions.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    onCameraPermissionGranted.onNext(true)
                else
                    onCameraPermissionGranted.onNext(false)
            }
        }
    }

    class QRCodeListener : QRCodeReaderView.OnQRCodeReadListener {
        val qrReceivedSubject: PublishSubject<String> = PublishSubject.create()
        override fun onQRCodeRead(text: String, points: Array<out PointF>) {
            qrReceivedSubject.onNext(text)
            qrReceivedSubject.onComplete()
        }
    }

    private fun replaceBodyFragmentWith(tag: String) {
        val fragment = mActivityFragmentsMap[tag] ?: return

        fragmentManager.beginTransaction().replace(R.id.bodyFragment, fragment, tag).commit()
    }

    private lateinit var mBindHostDialog: BindHostDialogFragment

    fun subscribeHostKeys(hostAddress: HostAddressHolder): Observable<ClientSecretKeys> {
        mBindHostDialog = BindHostDialogFragment()
        mBindHostDialog.show(fragmentManager, "dialog")

        return mBindHostDialog.onFragmentLoaded
            .flatMap { mBindHostDialog.subscribeHostClientKeys(hostAddress) }
    }

    fun subscribeClientCertificate(
        hostAddress: HostAddressHolder,
        sessionHash: String
    ): Observable<ClientCertificate?> {
        return mBindHostDialog.subscribeGetClientCertificate(hostAddress, sessionHash)
    }

    fun subscribeHostCertificate(hostAddress: HostAddressHolder, sessionHash: String): Observable<HostCertificate?> {
        return mBindHostDialog.subscribeGetHostCertificate(hostAddress, sessionHash)
    }

    fun onCertificatesUpdate() {

        logonTouchPresenter.onCertificatesUpdated(hostBridgeServiceRef.element!!)

        updateLogonTouchWidget(true)
    }

    fun onCertificatesClear() {
        applicationContext.deleteFile(AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME)
        applicationContext.deleteFile(AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME)

        mCurrentIdleStatusState = StatusState.DEVICE_NOT_BIND
        updateLogonTouchWidget(false)
    }

    fun dismissBindHostDialog() {
        mBindHostDialog.dismiss()
    }

    fun hostUnlock(hostAddress: HostAddressHolder): Flowable<Boolean> {
        return logonTouchPresenter.subscribeHostUnlock(hostAddress, hostBridgeServiceRef.element!!)
            .doOnError {
                when (it) {

                    !is UserCancelledException -> {
                        hostUnlockDialog.isError = true
                        hostUnlockDialog.title = "Sorry, error :-("
                        hostUnlockDialog.message = it.message ?: "Unknown error occurred"
                        hostUnlockDialog.cancelListener = View.OnClickListener {
                            dismissProgressDialog()
                        }
                    }

                    else -> {
                        dismissProgressDialog()
                    }

                }
            }
    }

    private fun updateLogonTouchWidget(bound: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, LogonTouchWidget::class.java))

        ids.forEach { id ->
            LogonTouchWidget.updateAppWidget(
                applicationContext,
                appWidgetManager,
                id,
                bound
            )
        }
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

    private fun dismissProgressDialog() {
        hostUnlockDialog.dismiss()
    }

    private fun migrateSharedPreferences() {
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val sharedPreferencesEditor = PreferenceManager.getDefaultSharedPreferences(this)

        if (preferences.contains(getString(R.string.ip_preference))) {
            sharedPreferencesEditor.edit()
                .putString(
                    getString(R.string.ip_preference),
                    preferences.getString(getString(R.string.ip_preference), getString(R.string.ip_default))
                )
                .apply()

            preferences.edit().remove(getString(R.string.ip_preference)).apply()
        }

        if (preferences.contains(getString(R.string.port_preference))) {
            sharedPreferencesEditor.edit()
                .putString(
                    getString(R.string.port_preference),
                    preferences.getString(getString(R.string.port_preference), getString(R.string.port_default))
                )
                .apply()

            preferences.edit().remove(getString(R.string.port_preference)).apply()
        }
    }

    companion object {
        private val LOG_TAG = MainActivity::class.java.simpleName

        private val SECURITY_LOCK_FRAGMENT = "SECURITY_LOCK_FRAGMENT"
        private val IDLE_STATUS_FRAGMENT = "IDLE_STATUS_FRAGMENT"

        private val PERMISSION_REQUEST_CAMERA = 0
        val REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1
    }
}

private fun IdleStatusFragment.setState(value: StatusState) {
    this.showStatusText(value.state)

    val drawable = when (value) {
        StatusState.HOST_UNLOCKED -> resources.getDrawable(R.drawable.unlocked)
        StatusState.DEVICE_BIND -> resources.getDrawable(R.drawable.bound)
        StatusState.DEVICE_NOT_BIND -> resources.getDrawable(R.drawable.not_bound)
        StatusState.HOST_NOT_UNLOCKED,
        StatusState.GENERAL_ERROR,
        StatusState.CONNECTION_TIMEOUT,
        StatusState.HOST_UNREACHABLE,
        StatusState.USER_CANCELLED,
        StatusState.NO_CLIENT_CERT -> resources.getDrawable(R.drawable.error)
        else -> null
    }

    showStatusImage(drawable)
}
