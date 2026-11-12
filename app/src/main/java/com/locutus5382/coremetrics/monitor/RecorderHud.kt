package com.locutus5382.coremetrics.monitor

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.locutus5382.coremetrics.telemetry.Snapshot
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Optional single-window HUD owned by :monitor only while a recording exists.
 * This never samples sensors and never animates. A small touch target handles
 * drag; all controller keys and touches outside the window reach the game.
 *
 * No overlay may be shown until the user explicitly enables it and grants SAW.
 */
internal class RecorderHud(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var label: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private fun dp(px: Float): Int = (px * context.resources.displayMetrics.density).roundToInt()
    fun permissionGranted(): Boolean = Settings.canDrawOverlays(context)
    fun attached(): Boolean = label?.isAttachedToWindow == true

    fun show(): String {
        if (!permissionGranted()) return "HUD_PERMISSION_MISSING"
        if (attached()) return "HUD_ALREADY_ATTACHED"
        val view = TextView(context).apply {
            text = "COREMETRICS  ● REC\nWaiting for telemetry…"
            setTextColor(Color.rgb(227, 245, 252))
            textSize = 12f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(231, 12, 24, 39), Color.argb(222, 16, 38, 54))
            ).apply {
                cornerRadius = dp(12f).toFloat()
                setStroke(dp(1f), Color.argb(160, 99, 207, 232))
            }
            elevation = dp(3f).toFloat()
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(22f)
            y = dp(76f)
            alpha = 0.94f
        }
        var lastX = 0f
        var lastY = 0f
        var baseX = 0
        var baseY = 0
        view.setOnTouchListener { _, ev ->
            when(ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX
                    lastY = ev.rawY
                    baseX = p.x
                    baseY = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // TOP|END means increasing x moves overlay left.
                    p.x = (baseX - (ev.rawX-lastX)).roundToInt().coerceAtLeast(0)
                    p.y = (baseY + (ev.rawY-lastY)).roundToInt().coerceAtLeast(0)
                    runCatching { windows.updateViewLayout(view,p) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        return try {
            windows.addView(view, p)
            label = view
            params = p
            "HUD_ATTACHED"
        } catch (e: Exception) {
            label = null
            params = null
            "HUD_ADD_FAILED_" + e.javaClass.simpleName
        }
    }

    fun update(s: Snapshot, count: Long) {
        if (!attached()) return
        fun num(v: Double?, unit: String): String =
            v?.let { String.format(Locale.US,"%.1f%s",it,unit) } ?: "—"
        label?.text = buildString {
            append("COREMETRICS   ● REC  ").append(count).append("\n")
            append("CPU ").append(num(s.cpuTempC,"°"))
                .append("   GPU ").append(num(s.gpuTempC,"°")).append("\n")
            append("CPU ").append(s.cpuPercent?.toString() ?: "—").append("%")
                .append("   GPU ").append(s.gpuPercent?.toString() ?: "—").append("%")
            append("\nBAT ").append(num(s.signedBatteryWatts," W"))
                .append("   NET")
            append("\nFPS ").append(
                s.surfaceFps?.let { String.format(Locale.US,"%.1f*",it) } ?: "N/A")
        }
    }

    fun hide(): String {
        val v = label ?: return "HUD_NOT_ATTACHED"
        return try {
            windows.removeViewImmediate(v)
            label = null
            params = null
            "HUD_REMOVED"
        } catch (e: Exception) {
            if (!v.isAttachedToWindow) {
                label = null
                params = null
                "HUD_REMOVED_AFTER_EXCEPTION"
            } else "HUD_REMOVE_FAILED_" + e.javaClass.simpleName
        }
    }
}
