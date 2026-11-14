package com.botaoswipe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

class SwipeService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var root: LinearLayout? = null
    private var posX = 80
    private var posY = 600

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rebuild() }

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        rebuild()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun cell(text: String, size: Int): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.argb(230, 255, 255, 255))
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.5f)
        val lp = LinearLayout.LayoutParams(size, size)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        tv.layoutParams = lp
        if (text.isNotEmpty()) {
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(Color.argb(70, 30, 30, 30))
            tv.background = bg
        }
        return tv
    }

    private fun arrow(text: String, size: Int, dx: Float, dy: Float): TextView {
        val tv = cell(text, size)
        tv.setOnClickListener { swipe(dx, dy) }
        return tv
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun rebuild() {
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }

        val size = dp(prefs.getInt("size", 52))
        val top = row()
        val bottom = row()

        val handle = cell("✥", size)
        top.addView(handle)
        top.addView(arrow("↑", size, 0f, -1f))
        top.addView(cell("", size))

        bottom.addView(arrow("←", size, -1f, 0f))
        bottom.addView(arrow("↓", size, 0f, 1f))
        bottom.addView(arrow("→", size, 1f, 0f))

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(top)
        layout.addView(bottom)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = posX
        p.y = posY

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _: View, e: MotionEvent ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    touchX = e.rawX
                    touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (e.rawX - touchX).toInt()
                    p.y = startY + (e.rawY - touchY).toInt()
                    posX = p.x
                    posY = p.y
                    wm.updateViewLayout(layout, p)
                }
            }
            true
        }

        wm.addView(layout, p)
        root = layout
    }

    private fun swipe(dx: Float, dy: Float) {
        val m = resources.displayMetrics
        val cx = m.widthPixels / 2f
        val cy = m.heightPixels / 2f
        val halfW = m.widthPixels * 0.15f
        val halfH = m.heightPixels * 0.15f
        val path = Path()
        path.moveTo(cx - dx * halfW, cy - dy * halfH)
        path.lineTo(cx + dx * halfW, cy + dy * halfH)
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        super.onDestroy()
    }
}
