package com.ingenico.logontouch.fragments

import android.app.Activity
import android.app.Application
import android.app.DialogFragment
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.dlazaro66.qrcodereaderview.QRCodeReaderView
import com.google.gson.Gson
import com.ingenico.logontouch.LogonTouchApp
import com.ingenico.logontouch.MainActivity
import com.ingenico.logontouch.R
import com.ingenico.logontouch.exception.CertificateNotReadyException
import com.ingenico.logontouch.exception.UserCancelledException
import com.ingenico.logontouch.service.HostBridgeService
import com.ingenico.logontouch.tools.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.ReplaySubject
import kotlinx.android.synthetic.main.fragment_host_status.*
import java.net.SocketTimeoutException
import javax.inject.Inject

/**
 * Created by vigursky on 23.11.2017.
 */
class BindHostDialogFragment : DialogFragment() {

    @Inject
    lateinit var mHttpClient: TCPHTTPClient

    private var mRequestCancelled = false
    private var mQRDecodeStarted = false
    private var mMainActivity: MainActivity? = null
    val onFragmentLoaded: ReplaySubject<Unit> = ReplaySubject.createWithSize(1)
    private val cancelRequestBtnObservable: Observable<Unit> = Observable.create<Unit> {
        statusCancelBtn.setOnClickListener {
            dismiss()
        }
    }.share()

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)

        (activity?.application as LogonTouchApp).mSecurityComponent?.inject(this)

        try {
            mMainActivity = activity as? MainActivity
        }catch (ex: ClassCastException){
            Log.e(LOG_TAG, "Cannot cast context to MainActivity!")
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        (context?.applicationContext as LogonTouchApp).mSecurityComponent?.inject(this)

        try {
            mMainActivity = context as? MainActivity
        }catch (ex: ClassCastException){
            Log.e(LOG_TAG, "Cannot cast context to MainActivity!")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
         inflater.inflate(R.layout.fragment_host_status, container, false).also {
            dialog.window.requestFeature(Window.FEATURE_NO_TITLE)
        }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        statusQRDecoder.setQRDecodingEnabled(false)
        statusQRDecoder.setAutofocusInterval(2000L)
        statusQRDecoder.setTorchEnabled(true)
        statusQRDecoder.setBackCamera()
        cancelRequestBtnObservable.subscribe()
        onFragmentLoaded.onNext(Unit)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        if(mRequestCancelled.not()){
            mRequestCancelled = true
            onFragmentLoaded.onError(UserCancelledException())
        }
    }

    fun subscribeHostClientKeys(hostAddress: HostAddressHolder): Observable<ClientSecretKeys>{
        val qrListener = MainActivity.QRCodeListener()
        showLoaderState()

        return Observable
                .fromCallable {
                    try{
                        return@fromCallable mHttpClient.requestClientCertificateIsReady(hostAddress)
                    }catch (ex: Exception){
                        if (mRequestCancelled.not()) throw ex
                        else return@fromCallable false
                    }
                }
                .subscribeOn(Schedulers.io())
                .doOnNext { if(it.not()) throw CertificateNotReadyException() }
                .flatMap {
                    mMainActivity?.requestCameraPermission() ?: Observable.just(false)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    when(it){
                        true  -> showQRDecodeState()
                        false -> throw UserCancelledException()
                    }
                }
                .flatMap {
                    startQRDetection(qrListener)
                    qrListener.qrReceivedSubject
                }
                .doOnNext {
                    stopQRDetection()
                }
                .doFinally { stopQRDetection() }
                .observeOn(Schedulers.io())
                .map {
                    Log.d(LOG_TAG, "Received qr code")
                    return@map Gson().fromJson(it, ClientSecretKeys::class.java)
                }
    }

    fun subscribeGetClientCertificate(hostAddress: HostAddressHolder, sessionHash: String): Observable<ClientCertificate?>{
        showLoaderState()

        return Observable
                .fromCallable {
                    return@fromCallable mHttpClient
                            .requestSessionObject(hostAddress, sessionHash, TCPHTTPClient.PATH_GET_PRIVATE_CERT, ClientCertificate::class.java)
                }
                .subscribeOn(Schedulers.io())
    }

    fun subscribeGetHostCertificate(hostAddress: HostAddressHolder, sessionHash: String): Observable<HostCertificate?>{
        showLoaderState()

        return Observable
                .fromCallable {
                    return@fromCallable mHttpClient
                            .requestSessionObject(hostAddress, sessionHash, TCPHTTPClient.PATH_GET_PUBLIC_CERT, HostCertificate::class.java)
                }
                .subscribeOn(Schedulers.io())
    }

    fun showLoaderState(){
        statusText.text = resources.getText(R.string.status_wait, "Wait while checking host availability")
        statusProgressBar.visibility = View.VISIBLE
        statusQRDecoder.visibility = View.GONE
    }

    fun showQRDecodeState(){
        statusText.text = resources.getText(R.string.status_decodeqr, "Please point camera on QR code generated on computer")
        statusProgressBar.visibility = View.GONE
        statusQRDecoder.visibility = View.VISIBLE
    }

    fun startQRDetection(qrDecodedCB: QRCodeReaderView.OnQRCodeReadListener){
        if(mQRDecodeStarted){
            return
        }

        statusQRDecoder.setQRDecodingEnabled(true)
        statusQRDecoder.setOnQRCodeReadListener(qrDecodedCB)
        statusQRDecoder.startCamera()
        mQRDecodeStarted = true
    }

    fun stopQRDetection(){
        if(mQRDecodeStarted.not()){
            return
        }

        statusQRDecoder.setQRDecodingEnabled(false)
        statusQRDecoder.setOnQRCodeReadListener(null)
        statusQRDecoder.stopCamera()
        mQRDecodeStarted = false
    }

    override fun onPause() {
        super.onPause()
        if(mQRDecodeStarted) stopQRDetection()

    }

    companion object {
        val LOG_TAG = BindHostDialogFragment::class.java.simpleName
    }
}