package com.ingenico.logontouch.fragments

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.firebase.crash.FirebaseCrash
import com.ingenico.logontouch.LogonTouchApp
import com.ingenico.logontouch.MainActivity
import com.ingenico.logontouch.R
import com.ingenico.logontouch.dict.StatusState
import com.ingenico.logontouch.exception.CertificateNotReadyException
import com.ingenico.logontouch.exception.DeviceNotBindException
import com.ingenico.logontouch.exception.UserCancelledException
import com.ingenico.logontouch.tools.*
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_main_header.*
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Created by vigursky on 17.11.2017.
 */

class MainHeaderFragment: Fragment(){

    @Inject
    lateinit var mHttpClient: TCPHTTPClient

    private var mMainActivity: MainActivity? = null
    private var mCertificateRequestDisposable: Disposable? = null
    private var mUnlockRequestDisposable: Disposable? = null
    private val requestCertBntObservable = Observable.create<Pair<String, String>>{e ->
        requestHostCertBtn.setOnClickListener{
            e.onNext(Pair(hostIpEdit.text.toString(), hostPortEdit.text.toString()))
        }
        e.setCancellable {
            requestHostCertBtn?.setOnClickListener(null)
        }
    }
    private val hostIpEditObservable = Observable.create<String> {
        val watcher = EmittingTextWatcher(it)
        hostIpEdit.addTextChangedListener(watcher)
        it.setCancellable {
            hostIpEdit?.removeTextChangedListener(watcher)
        }
    }
    private val hostPortEditObservable = Observable.create<String> {
        val watcher = EmittingTextWatcher(it)
        hostPortEdit.addTextChangedListener(EmittingTextWatcher(it))
        it.setCancellable {
            hostPortEdit?.removeTextChangedListener(watcher)
        }
    }
    private val clearBindingObservable = Observable.create<String> {e->
        clearHostBtn.setOnClickListener {
            e.onNext("")
        }
        e.setCancellable {
            clearHostBtn?.setOnClickListener(null)
        }
    }.share()

    private var ipEditDisposable: Disposable? = null
    private var portEditDisposable: Disposable? = null


    class EmittingTextWatcher(private val emitter: ObservableEmitter<String>): TextWatcher{
        override fun afterTextChanged(s: Editable?) {
            if(s == null) return
            emitter.onNext(s.toString())
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)

        (activity?.application as LogonTouchApp).mSecurityComponent?.inject(this)

        try {
            mMainActivity = activity as? MainActivity
        }catch (ex: ClassCastException){
            Log.e(MainHeaderFragment.LOG_TAG, "Cannot cast context to MainActivity!")
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        (context?.applicationContext as LogonTouchApp).mSecurityComponent?.inject(this)

        try {
            mMainActivity = context as? MainActivity
        }catch (ex: ClassCastException){
            Log.e(MainHeaderFragment.LOG_TAG, "Cannot cast context to MainActivity!")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_main_header, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requestCertBntObservable.filter {
            when{
                it.first.isEmpty() -> {
                    hostIpEdit.error = "Set LogonTouch server IP address"
                    return@filter false
                }
                isValidIPAddress(it.first).not() -> {
                    hostIpEdit.error = "IP address wrong format"
                    return@filter false
                }
                else -> return@filter true
            }
        }.filter {
            when{
                it.second.isEmpty() -> {
                    hostPortEdit.error = "Set LogonTouch server TCP Port"
                    return@filter false
                }
                isValidTCPPort(it.second).not() -> {
                    hostPortEdit.error = "TCP Port wrong format"
                    return@filter false
                }
                else -> return@filter true
            }
        }.map {
            HostAddressHolder(it.first, it.second.toInt())
        }.doOnNext {
            //disable view while making request
            setViewAndChildrenEnabled(view, false)
        }.subscribe {hostAddressEntry ->
            mCertificateRequestDisposable = mMainActivity
                    ?.subscribeHostKeys(hostAddressEntry)
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribe (
                    {
                        keysReceived(hostAddressEntry, it)
                    },
                    {
                        onErrorResponse(it)
                    }
            )
        }

        unlockHostBtn.setOnClickListener {
            val hostAddr = HostAddressHolder(hostIpEdit.text.toString(), hostPortEdit.text.toString().toInt())

            mUnlockRequestDisposable = (activity as? MainActivity)?.subscribeHostUnlock(hostAddr)?.subscribe(
                    {
                        when(it){
                            true -> mMainActivity?.mCurrentIdleStatusState = StatusState.HOST_UNLOCKED
                            false -> mMainActivity?.mCurrentIdleStatusState = StatusState.HOST_NOT_UNLOCKED
                        }
                        mCertificateRequestDisposable?.dispose()
                    },
                    {
                        propagateErrorStatusToActivity(it)
                        mCertificateRequestDisposable?.dispose()
                    }
            )
        }

        val clearIp = clearBindingObservable
                .map { resources.getString(R.string.ip_default) }
                .doOnNext {
                    hostIpEdit.setText(it)
                }

        ipEditDisposable = hostIpEditObservable.debounce(4, TimeUnit.SECONDS)
                .mergeWith(clearIp)
                .subscribe {
                    val preferences = mMainActivity?.getPreferences(Context.MODE_PRIVATE) ?: return@subscribe
                    with(preferences.edit()){
                        putString(getString(R.string.ip_preference), it)
                    }.commit()
                }

        val clearPort = clearBindingObservable
                .map { resources.getString(R.string.port_default) }
                .doOnNext {
                    hostPortEdit.setText(it)
                }

        portEditDisposable = hostPortEditObservable.debounce(4, TimeUnit.SECONDS)
                .mergeWith(clearPort)
                .subscribe {
                    val preferences = mMainActivity?.getPreferences(Context.MODE_PRIVATE) ?: return@subscribe
                    with(preferences.edit()){
                        putString(getString(R.string.port_preference), it)
                    }.commit()
                }

        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        val defaultIp = resources.getString(R.string.ip_default)
        val defaultPort = resources.getString(R.string.port_default)
        val ipAddr = sharedPref.getString(getString(R.string.ip_preference), defaultIp)
        val port = sharedPref.getString(getString(R.string.port_preference), defaultPort)

        hostIpEdit.setText(ipAddr)
        hostPortEdit.setText(port)
    }

    private fun keysReceived(hostAddressEntry: HostAddressHolder, sessionKeys: ClientSecretKeys){

        with(activity as MainActivity){
            mLocalKeystore.installWorkingSecretKey(sessionKeys.secretCredentialKey, AppLocalKeystore.CREDENTIAL_WORKING_KEY_ALIAS)
            mLocalKeystore.installWorkingSecretKey(sessionKeys.secretCredentialIV, AppLocalKeystore.CREDENTIAL_WORKING_IV_ALIAS)
            mLocalKeystore.installWorkingSecretKey(sessionKeys.privateKeyStoreKey, AppLocalKeystore.CLIENT_CERT_PASSPHRASE_ALIAS)

            val clientCertObs = subscribeClientCertificate(hostAddressEntry, sessionKeys.sessionHash)
            val hostCertObs   = subscribeHostCertificate(hostAddressEntry, sessionKeys.sessionHash)

            Observable.zip(clientCertObs, hostCertObs,
                    BiFunction<ClientCertificate?, HostCertificate?, Pair<ClientCertificate?, HostCertificate?>> { t1, t2 -> Pair(t1, t2) })
                    .observeOn(Schedulers.newThread())
                    .doOnNext {
                        try{
                            mHttpClient.uploadClientCertificateResult(hostAddressEntry, sessionKeys.sessionHash, true)
                        }catch (ex: Exception){
                            FirebaseCrash.logcat(Log.ERROR, LOG_TAG, "Failed to upload certificate result")
                            FirebaseCrash.report(ex)
                        }

                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                clientCertReceived(it.first)
                                hostCertReceived(it.second)
                                onCertificatesUpdate()
                            },
                            {onErrorResponse(it)},
                            {completeKeysRequest()}
                    )
        }
    }

    private fun clientCertReceived(certificate: ClientCertificate?){
        certificate ?: return

        activity.applicationContext
                .openFileOutput(AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME, Context.MODE_PRIVATE).use {
            it.write(certificate.cert)
        }

        Log.d(LOG_TAG, "Keystore ${AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME} stored to flash")
    }

    private fun hostCertReceived(certificate: HostCertificate?){
        certificate ?: return

        with(activity as MainActivity){
            mLocalKeystore.installWorkingSecretKey(String(certificate.publicKeyStoreKey), AppLocalKeystore.SERVER_CERT_PASSPHRASE_ALIAS)
            applicationContext.openFileOutput(AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME, Context.MODE_PRIVATE).use {
                it.write(certificate.publicCertificate)
            }
        }

        Log.d(LOG_TAG, "Keystore ${AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME} stored to flash")
    }

    private fun completeKeysRequest(){
        mCertificateRequestDisposable?.dispose()
        mMainActivity?.mCurrentIdleStatusState = StatusState.DEVICE_BIND
        mMainActivity?.dismissBindHostDialog()
        setViewAndChildrenEnabled(view, true)
    }

    private fun propagateErrorStatusToActivity(ex: Throwable){
        mMainActivity?.mCurrentIdleStatusState = when(ex){
            is SocketException              ->  StatusState.HOST_UNREACHABLE
            is SocketTimeoutException       ->  StatusState.HOST_UNREACHABLE
            is UserCancelledException       ->  StatusState.USER_CANCELLED
            is CertificateNotReadyException ->  StatusState.NO_CLIENT_CERT
            is DeviceNotBindException       ->  StatusState.DEVICE_NOT_BIND
            else                            ->  {
                ex.printStackTrace()
                StatusState.HOST_UNREACHABLE
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        ipEditDisposable?.dispose()
        portEditDisposable?.dispose()
    }

    private fun onErrorResponse(ex: Throwable){
        completeKeysRequest()
        propagateErrorStatusToActivity(ex)
    }

    private val IP_PATTERN = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")
    private val PORT_PATTERN = Pattern.compile(
            "^[0-9]{1,5}$")

    fun isValidIPAddress(ip: String): Boolean = IP_PATTERN.matcher(ip).matches()
    fun isValidTCPPort(port: String): Boolean =
            PORT_PATTERN.matcher(port).matches() && port.toInt() <= 0xFFFF

    companion object {
        val LOG_TAG = MainHeaderFragment::class.java.simpleName
    }
}