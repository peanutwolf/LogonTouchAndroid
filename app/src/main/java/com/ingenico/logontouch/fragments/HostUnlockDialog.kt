package com.ingenico.logontouch.fragments

import android.app.DialogFragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.ingenico.logontouch.R
import kotlinx.android.synthetic.main.fragment_host_unlock.*

class HostUnlockDialog : DialogFragment(){

    var title: CharSequence = "Unlock PC"
        set(value) {
            field = value
            if(tv_title != null)
                tv_title.text = value
        }

    var message: CharSequence = "Loading..."
        set(value) {
            field = value
            if(tv_message != null)
                tv_message.text = value
        }

    var cancelListener: View.OnClickListener? = null
        set(value) {
            field = value
            if(btn_cancel != null)
                btn_cancel.setOnClickListener(value)
        }

    var isError: Boolean = false
        set(value) {
            field = value
            if (pb_loader != null && iv_error != null){
                when(value){
                    true -> {
                        pb_loader.visibility = View.GONE
                        iv_error.visibility = View.VISIBLE
                    }
                    false -> {
                        pb_loader.visibility = View.VISIBLE
                        iv_error.visibility = View.GONE
                    }
                }
            }

        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_host_unlock, container, false).also {
//            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tv_title.text = title
        tv_message.text = message
        isError = isError
        btn_cancel.setOnClickListener(cancelListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        btn_cancel.setOnClickListener(null)
    }


}