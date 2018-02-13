package com.ingenico.logontouch.fragments

import kotlinx.android.synthetic.main.fragment_main_body.*

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.app.Fragment
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ingenico.logontouch.R

/**
 * Created by vigursky on 17.11.2017.
 */
class SecurityLockFragment : Fragment(){
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_main_body, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        bodyActionBtn?.setOnClickListener {
            SecuritySettingScreenDialog().show(fragmentManager, "SecuritySettings")
        }

    }

    class SecuritySettingScreenDialog: DialogFragment(){
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val onPositiveclick = { dialog: DialogInterface, which: Int ->
                startActivity(Intent("com.android.credentials.UNLOCK"))
//                activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }

            val dialog = AlertDialog.Builder(activity)
                    .setTitle("Set device PIN?")
                    .setMessage("You will be redirected to security setting screen")
                    .setPositiveButton("Yes", onPositiveclick)
                    .setNegativeButton("No", {_, _ -> dismiss()})
                    .create()

            return dialog
        }
    }
}