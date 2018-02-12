package com.ingenico.logontouch.fragments

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ingenico.logontouch.LogonTouchApp
import com.ingenico.logontouch.MainActivity
import com.ingenico.logontouch.R
import kotlinx.android.synthetic.main.fragment_idle_status.*

/**
 * Created by vigursky on 25.11.2017.
 */
class IdleStatusFragment: Fragment(){
    private var mStateImpl: IdleStatusState? = null

    interface IdleStatusState{
        fun onIdleStatusFragmentLoaded(statusFragment: IdleStatusFragment)
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        try {
            mStateImpl = activity as? MainActivity
        }catch (ex: ClassCastException){
            Log.e(IdleStatusFragment.LOG_TAG, "Cannot cast context to MainActivity!")
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        try {
            mStateImpl = context as? MainActivity
        }catch (ex: ClassCastException){
            Log.e(IdleStatusFragment.LOG_TAG, "Cannot cast context to MainActivity!")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_idle_status, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mStateImpl?.onIdleStatusFragmentLoaded(this)
    }

    fun showStatusText(text: String){
        textIdleStatus.text = text
    }

    companion object {
        val LOG_TAG = IdleStatusFragment::class.java.simpleName
    }
}