package com.ingenico.logontouch.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import com.ingenico.logontouch.tools.HostAddressHolder
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * Created by vigursky on 20.11.2017.
 */

class HostBridgeService: Service(){
    private lateinit var mServiceLooper: Looper
    private val mBridgeTaskPool = HashMap<HostAddressHolder, BridgeServiceHandler>()

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
