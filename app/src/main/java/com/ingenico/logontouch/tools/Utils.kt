package com.ingenico.logontouch.tools

import android.view.View
import android.view.ViewGroup
import android.animation.Animator
import android.animation.AnimatorListenerAdapter



/**
 * Created by vigursky on 24.11.2017.
 */
fun setViewAndChildrenEnabled(view: View, enabled: Boolean) {
    view.isEnabled = enabled
    if (view !is ViewGroup) return
    (0 until view.childCount)
            .asSequence()
            .map { view.getChildAt(it) }
            .forEach { setViewAndChildrenEnabled(it, enabled) }
}

fun View.animateView(toVisibility: Int, toAlpha: Float, duration: Int) {
    val show = toVisibility == View.VISIBLE
    if (show) {
        this.alpha = 0f
    }
    this.visibility = View.VISIBLE
    this.animate()
            .setDuration(duration.toLong())
            .alpha(if (show) toAlpha else 0F)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    this@animateView.visibility = toVisibility
                }
            })
}