package com.ingenico.logontouch.test

import kotlinx.android.synthetic.main.activity_register.*
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import com.ingenico.logontouch.R
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyStore

/**
 * Created by vigursky on 14.11.2017.
 */
class RegisterActivity: AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        btnLoadCertificate.setOnClickListener { loadClientCertificate() }
    }

    fun loadClientCertificate(){
        val logRequest = HttpLoggingInterceptor()
        logRequest.level = HttpLoggingInterceptor.Level.HEADERS

        val okClient = OkHttpClient.Builder().addInterceptor (logRequest).build()

        val url = HttpUrl.parse("http://127.0.0.1:7722/register")
                .newBuilder()
                .addQueryParameter("hash", "1234567")
                .build()

        val req = Request.Builder().url(url)
                .addHeader("Connection","close").build()
//        val req = Request.Builder().url("http://192.168.10.68:80").build()
//        var req = Request.Builder().url("http://192.168.10.68:8080/credential/status").build()
//        var req = Request.Builder().post(body).url("http://192.168.10.68:8080/credential/provide").build()
//        var req = Request.Builder().post(body).url("https://vk.com").build()

        okClient.newCall(req).enqueue(object: Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                Log.d(LOG_TAG, "Request failed")
            }

            override fun onResponse(call: Call?, response: Response?) {
                val cert = JSONObject(response?.body()?.string()).getString("cert")
                val certArr: ByteArray = Base64.decode(cert, Base64.DEFAULT)
                val keystore = KeyStore.getInstance("PKCS12")
                ByteArrayInputStream(certArr).use {
                    keystore.load(it, "keypass.key".toCharArray())
                }
                Log.d(LOG_TAG, "Request succeeded")
            }
        })

    }

    companion object {
        val LOG_TAG = RegisterActivity::class.java.simpleName
    }
}