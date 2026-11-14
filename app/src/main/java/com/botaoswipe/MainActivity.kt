package com.botaoswipe

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(pad, pad, pad, pad)

        val info = TextView(this)
        info.textSize = 16f
        info.text = "1) Toque no botão abaixo.\n" +
            "2) Escolha Botão Swipe e ative.\n" +
            "3) As setas aparecem na tela. Segure e arraste o ✥ para mover."
        col.addView(info)

        val btn = Button(this)
        btn.text = "Abrir acessibilidade"
        btn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        col.addView(btn)

        val label = TextView(this)
        label.textSize = 16f
        label.setPadding(0, pad, 0, 0)
        col.addView(label)

        val seek = SeekBar(this)
        seek.max = 60
        val cur = prefs.getInt("size", 52)
        seek.progress = cur - 30
        label.text = "Tamanho: $cur"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = "Tamanho: ${p + 30}"
                prefs.edit().putInt("size", p + 30).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        col.addView(seek)

        setContentView(col)
    }
}
