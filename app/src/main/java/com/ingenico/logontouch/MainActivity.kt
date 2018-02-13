package com.ingenico.logontouch

import android.Manifest
import android.app.Activity
import android.app.Fragment
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.security.keystore.UserNotAuthenticatedException
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.dlazaro66.qrcodereaderview.QRCodeReaderView
import com.ingenico.logontouch.dict.StatusState
import com.ingenico.logontouch.exception.UserCancelledException
import com.ingenico.logontouch.fragments.BindHostDialogFragment
import com.ingenico.logontouch.fragments.IdleStatusFragment
import com.ingenico.logontouch.fragments.SecurityLockFragment
import com.ingenico.logontouch.service.HostBridgeService
import com.ingenico.logontouch.tools.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.include_progress_overlay.*
import javax.inject.Inject


/**
 * Created by vigursky on 15.11.2017.
 */

class MainActivity: AppCompatActivity(), IdleStatusFragment.IdleStatusState{

    private lateinit var mKeyGuard: KeyguardManager

    @Inject
    lateinit var mLocalKeystore: AppLocalKeystore

    private var currentIdleStatusFragment: IdleStatusFragment? = null
    public  var mCurrentIdleStatusState: StatusState = StatusState.APPLICATION_LOADING
    set(value) {
        field = value
        currentIdleStatusFragment?.setState(value)
    }

    private val mActivityFragmentsMap:HashMap<String, Fragment> = HashMap()

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
        setContentView(R.layout.activity_main)
        (application as LogonTouchApp).mSecurityComponent?.inject(this)
        mKeyGuard = this.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun onStart() {
        super.onStart()
        with(Intent(this, HostBridgeService::class.java)){
            this@MainActivity.bindService(this, mHostBridgeServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        val headerFragmentView = findViewById(R.id.headerFragment)
        val locked = mKeyGuard.isKeyguardLocked
        val secure = mKeyGuard.isKeyguardSecure
        when{
            secure.not() -> {
                setViewAndChildrenEnabled(headerFragmentView, enabled = false)
                replaceBodyFragmentWith(SECURITY_LOCK_FRAGMENT)
            }
            locked       -> {
                setViewAndChildrenEnabled(headerFragmentView, enabled = false)
                startActivity(Intent("com.android.credentials.UNLOCK"))
            }
            else         ->{
                replaceBodyFragmentWith(IDLE_STATUS_FRAGMENT)
                if(mLocalKeystore.masterKeyAvailable().not()){
                    mLocalKeystore.generateMasterKey()
                }
                mCurrentIdleStatusState = when(checkClientCertsAvailable()){
                    true  -> StatusState.DEVICE_BIND
                    false -> StatusState.DEVICE_NOT_BIND
                }
                setViewAndChildrenEnabled(headerFragmentView, enabled = true)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(mHostBridgeServiceConnection)
    }

    private var onUserCredentialAuth: Subject<Boolean>? = null
    fun retryOnUserAuth(cause: Throwable): Observable<Boolean>{
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ){
            return Observable.error<Boolean>(cause)
        }else if(cause !is UserNotAuthenticatedException){
            return Observable.error<Boolean>(cause)
        }else if(!showAuthenticationScreen()){
            return Observable.just(true)
        }else{
            onUserCredentialAuth = PublishSubject.create()
            return onUserCredentialAuth!!
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun showAuthenticationScreen(): Boolean {
        val intent = mKeyGuard.createConfirmDeviceCredentialIntent(null, null)
        intent?.let {
            startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS)
            return true
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS -> {
                when(resultCode){
                    Activity.RESULT_OK  -> onUserCredentialAuth?.onNext(true)
                    else                -> onUserCredentialAuth?.onError(Exception("User not Authenticated"))
                }
            }
        }
    }

    private val onCameraPermissionGranted = PublishSubject.create<Boolean>()
    fun requestCameraPermission(): Observable<Boolean>{
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            return Observable.just(true)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CAMERA)
            return onCameraPermissionGranted
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            PERMISSION_REQUEST_CAMERA ->{
                if(permissions.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    onCameraPermissionGranted.onNext(true)
                else
                    onCameraPermissionGranted.onNext(false)
            }
        }
    }

    private fun checkClientCertsAvailable(): Boolean{
        val b1 = mLocalKeystore.masterKeyAvailable()
        val b2 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.CREDENTIAL_WORKING_KEY_ALIAS)
        val b3 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.CREDENTIAL_WORKING_IV_ALIAS)
        val b4 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.CLIENT_CERT_PASSPHRASE_ALIAS)
        val b5 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.SERVER_CERT_PASSPHRASE_ALIAS)
        val b6 = AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME in applicationContext.fileList()
        val b7 = AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME in applicationContext.fileList()

        return b1.and(b2).and(b3).and(b4).and(b5).and(b6).and(b7)
    }

    class QRCodeListener: QRCodeReaderView.OnQRCodeReadListener{
        val qrReceivedSubject: PublishSubject<String> = PublishSubject.create()
        override fun onQRCodeRead(text: String, points: Array<out PointF>) {
            qrReceivedSubject.onNext(text)
            qrReceivedSubject.onComplete()
        }
    }

    private fun replaceBodyFragmentWith(tag: String){
        val fragment = mActivityFragmentsMap[tag] ?: return

        fragmentManager.beginTransaction().replace(R.id.bodyFragment, fragment, tag).commit()
    }


    private lateinit var mBindHostDialog: BindHostDialogFragment

    fun subscribeHostKeys(hostAddress: HostAddressHolder): Observable<ClientSecretKeys>{
        mBindHostDialog = BindHostDialogFragment()
        mBindHostDialog.show(fragmentManager, "dialog")

        return mBindHostDialog.onFragmentLoaded
                .flatMap { mBindHostDialog.subscribeHostClientKeys(hostAddress) }

    }

    fun subscribeClientCertificate(hostAddress: HostAddressHolder, sessionHash: String): Observable<ClientCertificate?>{
        return mBindHostDialog.subscribeGetClientCertificate(hostAddress, sessionHash)
    }

    fun subscribeHostCertificate(hostAddress: HostAddressHolder, sessionHash: String): Observable<HostCertificate?>{
        return mBindHostDialog.subscribeGetHostCertificate(hostAddress, sessionHash)
    }

    fun dismissBindHostDialog(){
        mBindHostDialog.dismiss()
    }

    fun subscribeHostUnlock(hostAddress: HostAddressHolder): Observable<Boolean>{

        return Observable
                .fromCallable {
                    val credentialKey  = mLocalKeystore.readWorkingSecretKey(AppLocalKeystore.CREDENTIAL_WORKING_KEY_ALIAS)
                    val credentialIV  = mLocalKeystore.readWorkingSecretKey(AppLocalKeystore.CREDENTIAL_WORKING_IV_ALIAS)
                    val keyManagerPass = mLocalKeystore.readWorkingSecretKey(AppLocalKeystore.CLIENT_CERT_PASSPHRASE_ALIAS)
                    val trustManagerPass = mLocalKeystore.readWorkingSecretKey(AppLocalKeystore.SERVER_CERT_PASSPHRASE_ALIAS)
                    val keyManagerStore = mLocalKeystore.readKeyStore(AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME, keyManagerPass)
                    val trustManagerStore = mLocalKeystore.readKeyStore(AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME, trustManagerPass)
                    val client = HostHTTPSClient(mHostBridgeService,
                            keyManagerStore, keyManagerPass.toCharArray(),
                            trustManagerStore)
                    val res = client.postCredentialKey(hostAddress, credentialKey, credentialIV)
                    return@fromCallable res
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {progress_overlay.animateView(View.VISIBLE, 0.4f, 200)}
                .mergeWith(Observable.create { e ->
                    progress_overlay.setOnClickListener {
                        e.onError(UserCancelledException())
                    }
                    e.setCancellable {
                        progress_overlay.setOnClickListener(null)
                    }
                })
                .retryWhen { e -> e.flatMap { retryOnUserAuth(it) } }
                .doFinally {progress_overlay.animateView(View.GONE, 0f, 200) }
                .take(1)
    }

    private var mHostBridgeService: HostBridgeService? = null
    private val mHostBridgeServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            mHostBridgeService = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mHostBridgeService = (service as HostBridgeService.HostBridgeBinder).getService()
        }
    }

    companion object {
        private val LOG_TAG = MainActivity::class.java.simpleName

        private val  SECURITY_LOCK_FRAGMENT = "SECURITY_LOCK_FRAGMENT"
        private val  IDLE_STATUS_FRAGMENT = "IDLE_STATUS_FRAGMENT"

        private val PERMISSION_REQUEST_CAMERA = 0
        private val REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1
    }
}

private fun IdleStatusFragment.setState(value: StatusState) {
    this.showStatusText(value.state)
}
