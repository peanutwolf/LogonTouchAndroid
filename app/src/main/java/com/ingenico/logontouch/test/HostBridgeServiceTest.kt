package com.ingenico.logontouch.test

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

/**
 * Created by vigursky on 22.10.2017.
 */

class HostBridgeService: Service(){
    private val mBridgeServiceBinder = BridgetServiceBinder()
    private var mSelfSocket: ServerSocket = ServerSocket(7722)
    private lateinit var mServiceLooper: Looper
    private lateinit var mServiceHandler: ServiceHandler
    private val mSelfSocketObservable: Observable<Socket> = Observable.create {
        var clientSocket: Socket? = null
        try {
            clientSocket = mSelfSocket.accept()
            it.onNext(clientSocket)
        }catch (ex: SocketTimeoutException){
            clientSocket?.close()
            it.onError(ex)
        }
        it.onComplete()
    }
    private var subscription: Disposable? = null

    inner class ServiceHandler(looper: Looper) : Handler(looper) {
        var mRunning = true
        val buf: ByteBuffer = ByteBuffer.allocate(1000)

        override fun handleMessage(msg: Message) {
            while(mRunning){
                subscription =  mSelfSocketObservable.subscribe(this::onNextClient, this::onErrorClient)
            }

        }

        fun onNextClient(client: Socket){
            try {
                processBridgeLoop(client)
            }catch (ex: Exception){
                client.close()
                throw ex
            }
        }

        private fun processBridgeLoop(client: Socket){
            val writer = client.getOutputStream().buffered()
            val reader = client.getInputStream().buffered()

            while(!client.isClosed && client.isConnected ){

                mHostCommService?.writeHost({
                    buf.clear()
                    val available = reader.available()
                    val to_read = if(available > buf.capacity()) buf.capacity() else available
                    val read = reader.read(buf.array(), 0, to_read )
                    buf.position(read)
                    buf
                }())

                with((mHostCommService?.readHost())){
                    if(this == null)
                        client.close()
                    else{
                        writer.write(this.array(), 0, this.position())
                        writer.flush()
                    }

                }
            }

        }

        fun onErrorClient(ex: Throwable){
            ex.printStackTrace()
        }

    }

    private var mHostCommService: HostCommService? = null
    private val mHostCommServiceConnection = object : ServiceConnection{
        override fun onServiceDisconnected(name: ComponentName?) {
            mHostCommService = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mHostCommService = (service as HostCommService.CommServiceBinder).getService()
            mHostCommService?.mServiceHandler?.sendEmptyMessage(0)
        }
    }

    override fun onCreate() {
        val thread = HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()

        mServiceLooper = thread.looper
        mServiceHandler = ServiceHandler(mServiceLooper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mServiceHandler.sendEmptyMessage(0)

        with(Intent(this.applicationContext, HostCommService::class.java)){
            bindService(this, mHostCommServiceConnection, Context.BIND_AUTO_CREATE)
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        mServiceHandler.mRunning = false
        mSelfSocket.close()
        subscription?.dispose()
        unbindService(mHostCommServiceConnection)
        mServiceLooper.quit()
        super.onDestroy()
    }

    inner class BridgetServiceBinder: Binder(){
        fun getService(): HostBridgeService = this@HostBridgeService
    }
    override fun onBind(intent: Intent?): IBinder = mBridgeServiceBinder

    companion object {
        val LOG_TAG = HostBridgeService::class.java.name!!
    }
}
