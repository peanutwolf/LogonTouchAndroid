package com.ingenico.logontouch.test
import android.app.Service
import android.content.Intent
import android.os.*
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel


/**
 * Created by vigursky on 18.10.2017.
 */

class HostCommService: Service() {
    private var mServiceLooper: Looper? = null
    public  var mServiceHandler: ServiceHandler? = null
    private val mCommServiceBinder = CommServiceBinder()
    public  val mConnectionSubject: Subject<Boolean> = PublishSubject.create()
    private val mConnThread = SockeChannelConnectionThread()

    inner class ServiceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when(msg.what){
                0 -> mConnThread.start()
            }
        }
    }

    inner class SockeChannelConnectionThread: Thread(){
        private var connectedTmp = false
        private var mHostSocketChannel: SocketChannel? = null
        private var mHostConnected = false
        private val buf = ByteBuffer.allocate(1000)


        override fun run() {
            try {
                while (!this.isInterrupted){
                    if(mHostSocketChannel == null || mHostSocketChannel?.isOpen?.not()!! || mHostSocketChannel?.isConnected?.not()!!)
                        mHostConnected = hostConnect(testHostAddr)

                    if(connectedTmp != mHostConnected){
                        connectedTmp = mHostConnected
                        mConnectionSubject.onNext(mHostConnected)
                    }
                    Thread.sleep(1000)
                }
            }catch (ex: InterruptedException){
                ex.printStackTrace()
            }

        }

        private fun hostConnect(hostAddr: SocketAddress): Boolean{
            if(mHostSocketChannel == null)
                mHostSocketChannel = SocketChannel.open()

            try {
                mHostSocketChannel?.connect(hostAddr)
            } catch (ex: Throwable){
                mHostSocketChannel?.close()
                mHostSocketChannel = null
                return false
            }

            mHostSocketChannel?.configureBlocking(false)

            return true
        }

        @Synchronized fun writeHost(buf: ByteBuffer?){
            if(mHostSocketChannel == null){
                throw IOException()
            }

            if(buf == null){
                mHostSocketChannel?.close()
            }else if(buf.position() > 0){
                buf.flip()
                try {
                    while(buf.hasRemaining()) {
                        mHostSocketChannel?.write(buf)
                    }
                }catch (ex: Exception){
                    mHostSocketChannel?.close()
                    mHostSocketChannel = null
                    throw ex
                }
            }

        }

        @Synchronized fun readHost(): ByteBuffer?{
            if(mHostSocketChannel == null){
                throw IOException()
            }

            buf.clear()
            try {
                val res = mHostSocketChannel?.read(buf)
                if(res == -1) {
                    throw IOException()
                }
            }catch (ex: Exception){
                ex.printStackTrace()
                mHostSocketChannel?.close()
                mHostSocketChannel = null
                return null
            }



            return buf
        }
    }

    override fun onCreate() {
        val thread = HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()

        mServiceLooper = thread.looper
        mServiceHandler = ServiceHandler(mServiceLooper!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mServiceHandler?.sendEmptyMessage(0)

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        mConnThread.interrupt()
        mServiceLooper?.quit()
        super.onDestroy()
    }

    inner class CommServiceBinder: Binder(){
        fun getService(): HostCommService {
            return this@HostCommService
        }
    }

    override fun onBind(intent: Intent?): IBinder = mCommServiceBinder

    @Throws(IOException::class)
    fun writeHost(buf: ByteBuffer?){
        mConnThread.writeHost(buf)
    }

    @Throws(IOException::class)
    fun readHost(): ByteBuffer?{
        return mConnThread.readHost()
    }

    companion object {
        private val LOG_TAG = HostCommService::class.java.name!!
        private val testHostAddr = InetSocketAddress("192.168.1.1", 55470)

        private val WHAT_CONNECT_HOST = 0
    }
}