package com.ingenico.logontouch

import android.app.KeyguardManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.ingenico.logontouch.tools.AppLocalKeystore

/**
 * Implementation of App Widget functionality.
 */
class LogonTouchWidget : AppWidgetProvider() {
    private lateinit var mKeyGuard: KeyguardManager
    private lateinit var mLocalKeystore: AppLocalKeystore

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        mKeyGuard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        mLocalKeystore = AppLocalKeystore(context)
        val bound = when {
            !mKeyGuard.isKeyguardSecure -> false
            checkClientCertsAvailable(context) -> true
            else -> false
        }

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, bound)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private fun checkClientCertsAvailable(context: Context): Boolean {
        val b1 = mLocalKeystore.masterKeyAvailable()
        val b2 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.CREDENTIAL_WORKING_KEY_ALIAS)
        val b3 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.CREDENTIAL_WORKING_IV_ALIAS)
        val b4 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.CLIENT_CERT_PASSPHRASE_ALIAS)
        val b5 = mLocalKeystore.workingKeyAvailable(AppLocalKeystore.SERVER_CERT_PASSPHRASE_ALIAS)
        val b6 = AppLocalKeystore.CLIENT_PRIVATE_CERT_FILENAME in context.applicationContext.fileList()
        val b7 = AppLocalKeystore.SERVER_PUBLIC_CERT_FILENAME in context.applicationContext.fileList()

        return b1 and b2 and b3 and b4 and b5 and b6 and b7
    }

    companion object {
        private val TAG = LogonTouchWidget::class.java.simpleName

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            bound: Boolean
        ) {
            Log.d(TAG, "updateAppWidget")

            val pendingIntent: PendingIntent = Intent(context, WidgetActivity::class.java)
                .let { intent ->
                    PendingIntent.getActivity(context, 0, intent, 0)
                }

            val views = RemoteViews(context.packageName, R.layout.logon_touch_widget)
            if (bound) {
                views.setImageViewResource(R.id.iv_unlock_host, R.drawable.bound)
            } else {
                views.setImageViewResource(R.id.iv_unlock_host, R.drawable.not_bound)
            }

            views.setOnClickPendingIntent(R.id.iv_unlock_host, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

