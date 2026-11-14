package com.botaoswipe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SwipeService : AccessibilityService() {

    private class Marker(val view: TextView, val lp: WindowManager.LayoutParams)
    private class Item(val isSwipe: Boolean, val a: Marker, val b: Marker?)

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var counter: TextView? = null
    private var speedText: TextView? = null

    private val items = ArrayList<Item>()
    private val speeds = intArrayOf(1, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000)
    private var speedIdx = 5
    private var running = false
    private var step = 0
    private var plan: List<FloatArray> = emptyList()

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> resizeBubble() }

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        makeBubble()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(t: String) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    }

    private fun baseParams(w: Int, h: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        return p
    }

    private fun circle(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }

    private fun roundRect(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    // ---------- Bolinha ----------

    private fun makeBubble() {
        if (bubble != null) return
        val size = dp(prefs.getInt("size", 52))
        val tv = TextView(this)
        tv.text = "⚡"
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.5f)
        tv.setTextColor(Color.WHITE)
        tv.background = circle(Color.argb(210, 33, 150, 243))
        val lp = baseParams(size, size)
        lp.x = dp(16)
        lp.y = dp(200)

        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        tv.setOnTouchListener { _: View, e: MotionEvent ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x
                    sy = lp.y
                    tx = e.rawX
                    ty = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (!moved && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) {
                        moved = true
                        hidePanel()
                    }
                    if (moved) {
                        lp.x = sx + dx.toInt()
                        lp.y = sy + dy.toInt()
                        wm.updateViewLayout(tv, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onBubbleTap()
                }
            }
            true
        }
        wm.addView(tv, lp)
        bubble = tv
        bubbleLp = lp
    }

    private fun resizeBubble() {
        val b = bubble ?: return
        val lp = bubbleLp ?: return
        val s = dp(prefs.getInt("size", 52))
        lp.width = s
        lp.height = s
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, s * 0.5f)
        wm.updateViewLayout(b, lp)
    }

    private fun onBubbleTap() {
        if (running) {
            stopRun()
            return
        }
        if (panel == null) showPanel() else hidePanel()
    }

    // ---------- Menu ----------

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun btn(text: String, color: Int, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(10), dp(8), dp(10))
        t.background = roundRect(color, 10)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        t.layoutParams = lp
        t.setOnClickListener { onClick() }
        return t
    }

    private fun showPanel() {
        if (panel != null) return
        val b = bubbleLp ?: return

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.background = roundRect(Color.argb(235, 28, 28, 34), 16)

        val title = TextView(this)
        title.setTextColor(Color.WHITE)
        title.textSize = 15f
        title.gravity = Gravity.CENTER
        box.addView(title)
        counter = title

        val r1 = row()
        r1.addView(btn("+ Toque", Color.rgb(33, 150, 243)) { addItem(false) })
        r1.addView(btn("+ Arrastar", Color.rgb(76, 175, 80)) { addItem(true) })
        box.addView(r1)

        val r2 = row()
        r2.addView(btn("↩ Voltar", Color.rgb(255, 152, 0)) { undo() })
        r2.addView(btn("🗑 Lixeira", Color.rgb(229, 57, 53)) { clearAll() })
        box.addView(r2)

        val sp = TextView(this)
        sp.setTextColor(Color.WHITE)
        sp.textSize = 14f
        sp.gravity = Gravity.CENTER
        sp.setPadding(0, dp(6), 0, dp(2))
        box.addView(sp)
        speedText = sp

        val r3 = row()
        r3.addView(btn("⏪ Mais lento", Color.rgb(96, 125, 139)) { changeSpeed(1) })
        r3.addView(btn("⏩ Mais rápido", Color.rgb(96, 125, 139)) { changeSpeed(-1) })
        box.addView(r3)

        val r4 = row()
        r4.addView(btn("▶ INICIAR", Color.rgb(46, 125, 50)) { startRun() })
        box.addView(r4)

        val m = resources.displayMetrics
        val w = dp(250)
        val estH = dp(260)
        val lp = baseParams(w, WindowManager.LayoutParams.WRAP_CONTENT)
        val maxX = (m.widthPixels - w).coerceAtLeast(0)
        lp.x = b.x.coerceIn(0, maxX)
        val below = b.y + b.height + dp(6)
        lp.y = if (below + estH < m.heightPixels) below else (b.y - estH - dp(6)).coerceAtLeast(0)

        wm.addView(box, lp)
        panel = box
        refreshUi()
    }

    private fun hidePanel() {
        panel?.let {
            try { wm.removeView(it) } catch (e: Exception) { }
        }
        panel = null
        counter = null
        speedText = null
    }

    private fun refreshUi() {
        counter?.text = "Auto Click  " + items.size + "/100"
        speedText?.text = "Intervalo: " + speeds[speedIdx] + " ms"
    }

    private fun changeSpeed(dir: Int) {
        speedIdx = (speedIdx + dir).coerceIn(0, speeds.size - 1)
        refreshUi()
    }

    // ---------- Criar / apagar pontos ----------

    private fun makeMarker(text: String, color: Int, x: Int, y: Int): Marker {
        val s = dp(44)
        val tv = TextView(this)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.background = circle(color)
        val lp = baseParams(s, s)
        lp.x = x
        lp.y = y

        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        tv.setOnTouchListener { _: View, e: MotionEvent ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x
                    sy = lp.y
                    tx = e.rawX
                    ty = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = sx + (e.rawX - tx).toInt()
                    lp.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(tv, lp)
                }
            }
            true
        }
        wm.addView(tv, lp)
        return Marker(tv, lp)
    }

    private fun addItem(swipe: Boolean) {
        if (items.size >= 100) {
            toast("Máximo de 100")
            return
        }
        val n = (items.size + 1).toString()
        val m = resources.displayMetrics
        val off = (items.size % 6) * dp(14)
        val cx = m.widthPixels / 2 - dp(22) + off
        val cy = m.heightPixels / 2 - dp(22) + off

        val colA = if (swipe) Color.argb(190, 76, 175, 80) else Color.argb(190, 33, 150, 243)
        val a = makeMarker(n, colA, cx, cy)
        var b: Marker? = null
        if (swipe) {
            b = makeMarker(n, Color.argb(190, 229, 57, 53), cx + dp(90), cy)
        }
        items.add(Item(swipe, a, b))
        hidePanel()
        if (swipe) {
            toast("Arraste o verde (começo) e o vermelho (fim). Toque na bolinha ⚡ pro menu.")
        } else {
            toast("Arraste o círculo até onde quer tocar. Toque na bolinha ⚡ pro menu.")
        }
    }

    private fun removeMarker(m: Marker) {
        try { wm.removeView(m.view) } catch (e: Exception) { }
    }

    private fun undo() {
        if (items.isEmpty()) return
        val last = items.removeAt(items.size - 1)
        removeMarker(last.a)
        val lb = last.b
        if (lb != null) removeMarker(lb)
        refreshUi()
    }

    private fun clearAll() {
        for (i in items) {
            removeMarker(i.a)
            val ib = i.b
            if (ib != null) removeMarker(ib)
        }
        items.clear()
        refreshUi()
    }

    // ---------- Rodar ----------

    private fun setTouchable(m: Marker, touchable: Boolean) {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        m.lp.flags = if (touchable) base else base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        m.view.alpha = if (touchable) 1f else 0.5f
        try { wm.updateViewLayout(m.view, m.lp) } catch (e: Exception) { }
    }

    private fun setAllTouchable(touchable: Boolean) {
        for (i in items) {
            setTouchable(i.a, touchable)
            val ib = i.b
            if (ib != null) setTouchable(ib, touchable)
        }
    }

    private fun startRun() {
        if (items.isEmpty()) {
            toast("Crie um toque ou arrastar primeiro")
            return
        }
        val list = ArrayList<FloatArray>()
        val loc = IntArray(2)
        for (i in items) {
            i.a.view.getLocationOnScreen(loc)
            val x1 = loc[0] + i.a.view.width / 2f
            val y1 = loc[1] + i.a.view.height / 2f
            var x2 = x1
            var y2 = y1
            val bm = i.b
            if (i.isSwipe && bm != null) {
                bm.view.getLocationOnScreen(loc)
                x2 = loc[0] + bm.view.width / 2f
                y2 = loc[1] + bm.view.height / 2f
            }
            list.add(floatArrayOf(x1, y1, x2, y2, if (i.isSwipe) 1f else 0f))
        }
        plan = list
        step = 0
        running = true
        hidePanel()
        setAllTouchable(false)
        bubble?.text = "■"
        bubble?.background = circle(Color.argb(220, 229, 57, 53))
        toast("Rodando. Toque na bolinha pra parar.")
        runStep()
    }

    private fun runStep() {
        if (!running || plan.isEmpty()) return
        val s = plan[step % plan.size]
        step++
        val path = Path()
        path.moveTo(s[0], s[1])
        val dur: Long
        if (s[4] == 1f) {
            path.lineTo(s[2], s[3])
            dur = 120L
        } else {
            dur = 1L
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, dur)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                next()
            }
            override fun onCancelled(g: GestureDescription?) {
                next()
            }
        }
        val ok = dispatchGesture(gesture, cb, null)
        if (!ok) next()
    }

    private fun next() {
        if (running) {
            handler.postDelayed({ runStep() }, speeds[speedIdx].toLong())
        }
    }

    private fun stopRun() {
        running = false
        handler.removeCallbacksAndMessages(null)
        setAllTouchable(true)
        bubble?.text = "⚡"
        bubble?.background = circle(Color.argb(210, 33, 150, 243))
        toast("Parado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (::wm.isInitialized) {
            hidePanel()
            for (i in items) {
                removeMarker(i.a)
                val ib = i.b
                if (ib != null) removeMarker(ib)
            }
            items.clear()
            bubble?.let {
                try { wm.removeView(it) } catch (e: Exception) { }
            }
            bubble = null
        }
        super.onDestroy()
    }
}
