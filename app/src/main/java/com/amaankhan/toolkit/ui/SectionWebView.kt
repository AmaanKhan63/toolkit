package com.amaankhan.toolkit.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/** Keeps WebView scrolling, selection and zoom; turns pages only on a horizontal edge swipe. */
internal class SectionWebView(context: Context) : WebView(context) {
    var onNavigate: ((Int) -> Unit)? = null
    private var startX = 0f
    private var startY = 0f
    private var eligible = false
    private var navigating = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    @Suppress("DEPRECATION")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; navigating = false
                eligible = onNavigate != null && scale <= resources.displayMetrics.density * 1.05f
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                eligible = false; navigating = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> if (eligible && event.pointerCount == 1) {
                val dx = event.x - startX; val dy = event.y - startY
                if (!navigating) {
                    if (event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout() ||
                        abs(dy) > slop && abs(dy) >= abs(dx)) eligible = false
                    else if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                        if (canScrollHorizontally(if (dx < 0) 1 else -1)) eligible = false
                        else {
                            navigating = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            val cancel = MotionEvent.obtain(event)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            super.onTouchEvent(cancel); cancel.recycle()
                        }
                    }
                }
                if (navigating) return true
            }
            MotionEvent.ACTION_UP -> if (navigating) {
                navigating = false; eligible = false
                parent?.requestDisallowInterceptTouchEvent(false)
                val dx = event.x - startX
                if (abs(dx) >= 56 * resources.displayMetrics.density) onNavigate?.invoke(if (dx < 0) 1 else -1)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
