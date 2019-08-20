package com.ingenico.logontouch.service

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import com.ingenico.logontouch.tools.AppLocalKeystore
import com.ingenico.logontouch.tools.HostAddressHolder
import com.ingenico.logontouch.tools.HostCertificatePair
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.suspendCoroutine

/**
 * Created by vigursky on 20.11.2017.
 */

class HostBridgeService: Service(), CoroutineScope{
    private lateinit var mServiceLooper: Looper
    private val mBridgeTaskPool = HashMap<HostAddressHolder, BridgeServiceHandler>()

    private val keyStoreCacheFailed: AtomicBoolean = AtomicBoolean(false)
    private var keyStoreCache: KeyStore? = null
    private var trustStoreCache: KeyStore? = null

    override val coroutineContext: CoroutineContext = SupervisorJob()

    private inner class BridgeServiceHandler(looper: Looper, val hostAddr: HostAddressHolder): Handler(looper){
        val mServerSocket:                 ServerSocket             = ServerSocket()
        private val mAcceptObservable:     Observable<ServerSocket> = Observable.just(mServerSocket)
        private var mChannelDataExchanger: ChannelDataExchanger?    = null
        private var mClientChannel:        IOChannel?               = null
        private var mHostChannel:          IOChannel?               = null
        private val mHostAddr: InetSocketAddress

        init {
            mServerSocket.reuseAddress = true
            mServerSocket.bind(InetSocketAddress(null as InetAddress?, BIND_AUTO_PORT), SINGLE_BACKLOG)
            mHostAddr = InetSocketAddress(hostAddr.ip, hostAddr.port)
        }

        private val buf: ByteBuffer = ByteBuffer.allocate(2000)
        var mAcceptDisposable: Disposable? = null

        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            mAcceptDisposable = mAcceptObservable.subscribe ({ serverSocket ->
                try{
                    mClientChannel = ClientTCPSocketConnection(serverSocket.accept())

                    mHostChannel   = HostTCPChannelConnection(mHostAddr)
                    (mHostChannel as? HostTCPChannelConnection)?.openChannel()

                    mChannelDataExchanger = ChannelDataExchanger(mClientChannel, mHostChannel, buf)
                    mChannelDataExchanger?.exchangeLoop()
                }finally {
                    Log.d(LOG_TAG, "Finally close right/left channels")
                    mClientChannel?.closeChannel()
                    mHostChannel?.closeChannel()
                }
            },
            {
                onErrorClient(it)
            })
        }

        fun onErrorClient(ex: Throwable){
            ex.printStackTrace()
        }

        fun interrupt(){
            Log.d(LOG_TAG, "Performing interrupt for ChannelExchanger")
            mChannelDataExchanger?.interrupt()
            mHostChannel?.closeChannel()
            mClientChannel?.closeChannel()
        }
    }

    fun openHostBridge(hostAddr: HostAddressHolder): Int{
        var bridgeHandler = mBridgeTaskPool[hostAddr]

        if(bridgeHandler == null){
            bridgeHandler = BridgeServiceHandler(mServiceLooper, hostAddr)
            mBridgeTaskPool.put(hostAddr, bridgeHandler)
        }

        bridgeHandler.sendEmptyMessage(0)

        return bridgeHandler.mServerSocket.localPort
    }

    fun closeHostBridge(hostAddr: HostAddressHolder){
        mBridgeTaskPool[hostAddr]?.interrupt()
    }

    fun readHostCertificates(appKeystore: AppLocalKeystore): Single<HostCertificatePair> {

        return Single.fromCallable {
            runBlocking (Dispatchers.IO){
                val keyStore = async {
                    if(keyStoreCache != null){
                        Log.v(LOG_TAG, "Using keystore cached certificate")
                        return@async keyStoreCache!!
                    }

                    Log.d(LOG_TAG, "Reading keystore ${AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME}")
                    val keyManagerPass = appKeystore.readWorkingSecretKey(AppLocalKeystore.CLIENT_CERT_PASSPHRASE_ALIAS)
                    appKeystore.readKeyStore(AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME, keyManagerPass).also {
                        keyStoreCache = it
                    }
                }

                val trustStore = async {
                    if(trustStoreCache != null){
                        Log.v(LOG_TAG, "Using truststore cached certificate")
                        return@async trustStoreCache!!
                    }

                    Log.d(LOG_TAG, "Reading keystore ${AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME}")
                    val trustManagerPass = appKeystore.readWorkingSecretKey(AppLocalKeystore.SERVER_CERT_PASSPHRASE_ALIAS)
                    appKeystore.readKeyStore(AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME, trustManagerPass).also {
                        trustStoreCache = it
                    }
                }

                return@runBlocking HostCertificatePair(keyStore.await(), trustStore.await())
            }
        }
            .doOnSuccess {
                keyStoreCacheFailed.set(false)
                Log.d(LOG_TAG, "Success to read host certificates")
            }
            .doOnError {
                keyStoreCacheFailed.set(true)
                Log.e(LOG_TAG, "Failed to read host certificates", it)
            }
    }

    fun clearCertificateCache(){
        keyStoreCache = null
        trustStoreCache = null
    }

    override fun onCreate() {
        val thread = HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        mServiceLooper = thread.looper
    }

    override fun onDestroy() {
        super.onDestroy()

        mServiceLooper.quit()
        for ((_, handler) in mBridgeTaskPool){
            handler.mAcceptDisposable?.dispose()
            handler.mServerSocket.close()
        }
    }

    inner class HostBridgeBinder: Binder() {
        fun getService(): HostBridgeService = this@HostBridgeService
    }

    override fun onBind(intent: Intent?): IBinder = HostBridgeBinder()

    companion object {
        val BIND_AUTO_PORT = 0
        val SINGLE_BACKLOG = 1
        val LOG_TAG = HostBridgeService::class.java.simpleName
    }
}

abstract class IOChannel {
    abstract val LOG_TAG: String

    abstract fun writeChannel(buf: ByteBuffer): Int
    abstract fun readChannel(buf: ByteBuffer): Int
    abstract fun closeChannel()
}

class HostTCPChannelConnection(val hostAddr: InetSocketAddress): IOChannel(){
    private var mHostSocketChannel: SocketChannel? = null
    override val LOG_TAG: String
        get() = HostTCPChannelConnection::class.java.simpleName

    fun openChannel(){
        mHostSocketChannel = SocketChannel.open()
        mHostSocketChannel?.socket()?.reuseAddress = true
        mHostSocketChannel?.connect(hostAddr)
        mHostSocketChannel?.configureBlocking(false)
    }

    override fun writeChannel(buf: ByteBuffer): Int{
        var written = 0
        if(buf.position() <= 0) return 0
        if(mHostSocketChannel == null){
            throw IOException("Can't write to channel. IOChannel is not opened")
        }
        buf.apply { this.flip() }
        while(buf.hasRemaining()) {
            written += mHostSocketChannel!!.write(buf)
        }
        if(written != 0)
            Log.d(LOG_TAG, "Written bytes=[$written]")
        return written
    }

    override fun readChannel(buf: ByteBuffer): Int{
        var read = 0
        if(mHostSocketChannel == null){
            throw IOException("Can't read from channel. IOChannel is not opened")
        }
        read = mHostSocketChannel!!.read(buf)
        if(read != 0)
            Log.d(LOG_TAG, "Received bytes size=[$read]")
        return read
    }

    override fun closeChannel(){
        mHostSocketChannel?.close()
    }

}

class ClientTCPSocketConnection(val socket: Socket): IOChannel(){
    var mSocketInputStream: InputStream = socket.getInputStream()
    var mSocketOutputStream: OutputStream = socket.getOutputStream()
    override val LOG_TAG: String
        get() = ClientTCPSocketConnection::class.java.simpleName

    override fun writeChannel(buf: ByteBuffer): Int {
        val to_write = buf.position()
        mSocketOutputStream.write(buf.array(), 0, to_write)
        mSocketOutputStream.flush()
        if(to_write != 0)
            Log.d(LOG_TAG, "Written bytes=[$to_write]")
        return to_write
    }

    override fun readChannel(buf: ByteBuffer): Int {
        val available = mSocketInputStream.available()
        val to_read = if(available > buf.capacity()) buf.capacity() else available
        val read = mSocketInputStream.read(buf.array(), 0, to_read )
        if(read != 0)
            Log.d(LOG_TAG, "Received bytes size=[$read]")
        if(read >= 0) buf.position(read)
        return read
    }

    override fun closeChannel() {
        socket.close()
    }
}

class ChannelDataExchanger(private val connectionLeft: IOChannel?,
                           private val connectionRight: IOChannel?,
                           private val backingBuffer: ByteBuffer){
    private var mInterrupt = false

    fun exchangeLoop(){
        val nop = {}

        loop@ while(!mInterrupt){
            backingBuffer.clear()
            when(connectionLeft?.readChannel(backingBuffer)){
                -1 -> break@loop
                0  -> nop()
                else -> connectionRight?.writeChannel(backingBuffer)
            }

            backingBuffer.clear()
            when(connectionRight?.readChannel(backingBuffer)){
                -1 -> break@loop
                0  -> nop()
                else -> connectionLeft?.writeChannel(backingBuffer)
            }
        }

    }

    fun interrupt(){
        mInterrupt = true
    }
}
