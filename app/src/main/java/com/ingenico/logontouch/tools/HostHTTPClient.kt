package com.ingenico.logontouch.tools

import com.google.gson.Gson
import com.ingenico.logontouch.service.HostBridgeService
import okhttp3.*
import java.security.*
import java.util.*
import javax.net.ssl.*
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import android.net.NetworkInfo
import android.net.ConnectivityManager
import android.os.Build
import javax.inject.Inject
import javax.net.SocketFactory


/**
 * Created by vigursky on 18.11.2017.
 */

abstract class HostClient(private val mHostBridgeService: HostBridgeService?){

    protected fun requestWithRedirects(url: HttpUrl, httpClient: OkHttpClient): Boolean{
        val resp: Response
        val hostAddr = HostAddressHolder(url.host(), url.port())
        try {
            val port = mHostBridgeService?.openHostBridge(hostAddr) ?: return false

            val newURL = url.newBuilder().host("127.0.0.1").port(port).build()
            val req = Request.Builder()
                    .url(newURL)
                    .addHeader("Connection","close")
                    .build()

            resp = httpClient.newCall(req).execute()
            when{
                resp.isSuccessful     -> return true
                resp.isRedirect.not() -> return false
            }
        }finally {
            mHostBridgeService?.closeHostBridge(hostAddr)
        }

        val redirectURL = HttpUrl.parse(resp.header("Location"))
                .newBuilder()
                .host(hostAddr.ip)
                .build()

        return requestWithRedirects(redirectURL, httpClient)
    }

    protected fun <T> requestWithRedirects(url: HttpUrl, httpClient: OkHttpClient, clazz: Class<T>): T?{
        val resp: Response
        val hostAddr = HostAddressHolder(url.host(), url.port())
        try {
            val port = mHostBridgeService?.openHostBridge(hostAddr) ?: return null

            val newURL = url.newBuilder().host("127.0.0.1").port(port).build()
            val req = Request.Builder().url(newURL)
                    .addHeader("Connection","close").build()

            resp = httpClient.newCall(req).execute()
            when{
                resp.isSuccessful     -> return resp.body().string().let { Gson().fromJson(it, clazz) }
                resp.isRedirect.not() -> return null
            }
        }finally {
            mHostBridgeService?.closeHostBridge(hostAddr)
        }

        val redirectURL = HttpUrl.parse(resp.header("Location"))
                .newBuilder()
                .host(hostAddr.ip).build()

        return requestWithRedirects(redirectURL, httpClient, clazz)
    }

    protected fun postWithRedirects(url: HttpUrl, httpClient: OkHttpClient, body: RequestBody): Boolean{
        val resp: Response
        val hostAddr = HostAddressHolder(url.host(), url.port())
        try {
            val port = mHostBridgeService?.openHostBridge(hostAddr) ?: return false

            val newURL = url.newBuilder().host("127.0.0.1").port(port).build()
            val req = Request.Builder()
                    .url(newURL)
                    .post(body)
                    .addHeader("Connection","close")
                    .build()

            resp = httpClient.newCall(req).execute()
            when{
                resp.isSuccessful     -> return true
                resp.isRedirect.not() -> return false
            }
        }finally {
            mHostBridgeService?.closeHostBridge(hostAddr)
        }

        val redirectURL = HttpUrl.parse(resp.header("Location"))
                .newBuilder()
                .host(hostAddr.ip)
                .build()

        return postWithRedirects(redirectURL, httpClient, body)
    }
}



class HostHTTPClient(mHostBridgeService: HostBridgeService?): HostClient(mHostBridgeService){
    private val mHttpClient = OkHttpClient.Builder().build()

    fun <T> requestSessionObject(hostAddr: HostAddressHolder, sessionHash: String, certPath: String, clazz: Class<T>): T? {
        val url = HttpUrl.parse("http://${hostAddr.ip}:${hostAddr.port}/$certPath")
                .newBuilder()
                .addQueryParameter(QUERY_PARAM_HASH, sessionHash)
                .build()

        return requestWithRedirects(url, mHttpClient, clazz)
    }

    fun requestClientCertificateIsReady(hostAddr: HostAddressHolder): Boolean{
        val url = HttpUrl.parse("http://${hostAddr.ip}:${hostAddr.port}/$PATH_HOST_STATUS")
                .newBuilder()
                .build()

        return requestWithRedirects(url, mHttpClient)
    }

    companion object {
        val PATH_HOST_REGISTER    = "external/register"
        val PATH_HOST_STATUS      = "external/register/status"
        val PATH_GET_PUBLIC_CERT  = "external/register/public_cert"
        val PATH_GET_PRIVATE_CERT  = "external/register/private_cert"


        val QUERY_PARAM_HASH     = "hash"
    }

}

class HostHTTPSClient(mHostBridgeService: HostBridgeService?,
                      keyManagerStore: KeyStore?,
                      keyPass: CharArray?,
                      trustManagerStore: KeyStore? = null): HostClient(mHostBridgeService){
    private val mTrustManagers: Array<TrustManager> = initTrustManager(trustManagerStore)
    private var mHttpClient: OkHttpClient

    init {
        mHttpClient = OkHttpClient.Builder()
                .sslSocketFactory(getSSLSocketContext(keyManagerStore!!, keyPass!!).socketFactory)
                .hostnameVerifier { _, _ -> return@hostnameVerifier true }
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }

    private fun getSSLSocketContext(keyManagerStore: KeyStore, keyPass: CharArray): SSLContext {
        val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyManagerStore, keyPass)

        val sslContext: SSLContext = SSLContext.getInstance("SSL")
        sslContext.init(keyManagerFactory.keyManagers, mTrustManagers, SecureRandom())

        return sslContext
    }

    private fun initTrustManager(trustManagerStore: KeyStore?): Array<TrustManager>{
        return if(trustManagerStore != null){
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(trustManagerStore)
            trustManagerFactory.trustManagers
        }else{
            val unsafeTrustM = object : X509TrustManager{
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }
                override fun getAcceptedIssuers(): Array<X509Certificate?> {
                    return arrayOfNulls(0)
                }
            }
            arrayOf(unsafeTrustM)
        }
    }


    fun requestServerStatus(hostAddr: HostAddressHolder): Boolean{
        val url = HttpUrl.parse("http://${hostAddr.ip}:${hostAddr.port}/$PATH_HOST_STATUS")
                .newBuilder()
                .build()

        return requestWithRedirects(url, mHttpClient)
    }

    fun postCredentialKey(hostAddr: HostAddressHolder, credentialKey: String, credentialIV: String): Boolean{
        val url = HttpUrl.parse("http://${hostAddr.ip}:${hostAddr.port}/$PATH_HOST_PROVIDE")
                .newBuilder()
                .build()
        val JSON = MediaType.parse("application/json; charset=utf-8")
        val body: RequestBody = RequestBody.create(JSON,
                """{"key" : "$credentialKey", "iv" : "$credentialIV"}""")

        return postWithRedirects(url, mHttpClient, body)
    }

    companion object {
        val PATH_HOST_STATUS     = "external/credential/status"
        val PATH_HOST_PROVIDE    = "external/credential/provide"

        val QUERY_PARAM_KEY      = "key"
    }
}

class TCPHTTPClient @Inject constructor(private val mConnectivityManager: ConnectivityManager) {
    private val mHttpClient: OkHttpClient

    init {
        mHttpClient = OkHttpClient.Builder().apply {
            socketFactory(getTCPSocketFactory(mConnectivityManager, SocketFactory.getDefault()))
        }.build()
    }

    private fun getTCPSocketFactory(connectivityManager: ConnectivityManager, default: SocketFactory): SocketFactory {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return connectivityManager.allNetworks.find {
                        val netInfo = connectivityManager.getNetworkInfo(it)
                        when(netInfo.type){
                            ConnectivityManager.TYPE_WIFI      -> netInfo.state == NetworkInfo.State.CONNECTED
                            ConnectivityManager.TYPE_ETHERNET  -> netInfo.state == NetworkInfo.State.CONNECTED
                            else -> false
                        }
                    }?.socketFactory ?: default
        }else {
            connectivityManager.allNetworkInfo.find {
                when(it.type){
                    ConnectivityManager.TYPE_WIFI      -> it.state == NetworkInfo.State.CONNECTED
                    ConnectivityManager.TYPE_ETHERNET  -> it.state == NetworkInfo.State.CONNECTED
                    else -> false
                }
            }?.apply {
                connectivityManager.networkPreference = this.type
            }

            return default
        }
    }

    fun <T> requestSessionObject(hostAddr: HostAddressHolder, sessionHash: String, certPath: String, clazz: Class<T>): T? {
        val url = HttpUrl.parse("http://${hostAddr.ip}:${hostAddr.port}/$certPath")
                .newBuilder()
                .addQueryParameter(QUERY_PARAM_HASH, sessionHash)
                .build()

        val req = Request.Builder().url(url).build()
        val resp = mHttpClient.newCall(req).execute()
        return when{
            resp.isSuccessful     -> resp.body().string().let { Gson().fromJson(it, clazz) }
            else                  -> null
        }
    }

    fun requestClientCertificateIsReady(hostAddr: HostAddressHolder): Boolean{
        val url = HttpUrl.parse("http://${hostAddr.ip}:${hostAddr.port}/$PATH_HOST_STATUS")
                .newBuilder()
                .build()

        val req = Request.Builder().url(url).build()
        val resp = mHttpClient.newCall(req).execute()
        return when{
            resp.isSuccessful     -> true
            else                  -> false
        }
    }

    fun uploadClientCertificateResult(hostAddr: HostAddressHolder, sessionHash: String, result: Boolean): Boolean{
        val url = HttpUrl.parse("http://${hostAddr.ip}:${hostAddr.port}/$PATH_HOST_STATUS")
                .newBuilder()
                .addQueryParameter(QUERY_PARAM_HASH, sessionHash)
                .addQueryParameter(QUERY_PARAM_RESULT, result.toString())
                .build()

        val req = Request.Builder().url(url).build()
        val resp = mHttpClient.newCall(req).execute()
        return when{
            resp.isSuccessful     -> true
            else                  -> false
        }
    }

    companion object {
        val PATH_HOST_REGISTER    = "external/register"
        val PATH_HOST_STATUS      = "external/register/status"
        val PATH_GET_PUBLIC_CERT  = "external/register/public_cert"
        val PATH_GET_PRIVATE_CERT  = "external/register/private_cert"


        val QUERY_PARAM_HASH     = "hash"
        val QUERY_PARAM_RESULT   = "result"
    }

}