package com.example.numberocr.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.numberocr.capture.ScreenCaptureService
import com.example.numberocr.results.ResultsActivity

class OverlayController(private val context: Context, private val onStart: () -> Unit, private val onStop: () -> Unit) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val selector = SelectorView(context)
    private val selectorParams = WindowManager.LayoutParams(320, 180,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 260 }
    private enum class Shape { RECTANGLE, SQUARE, CIRCLE }
    private var shape = Shape.RECTANGLE

    private val bar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(10, 8, 10, 8)
        background = rounded(0xEE172033.toInt(), 16f)
    }
    private val barParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 190 }

    fun show() {
        wm.addView(selector, selectorParams)
        val start = button("Record / Start OCR") { onStart() }
        val stop = button("Stop") { onStop() }
        val results = button("View Results") { context.startActivity(android.content.Intent(context, ResultsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        lateinit var shapeButton: TextView
        shapeButton = button("Shape: Rectangle") { cycleShape(shapeButton) }
        bar.addView(start); bar.addView(stop); bar.addView(results); bar.addView(shapeButton); wm.addView(bar, barParams)
    }
    fun hide() { runCatching { wm.removeView(selector); wm.removeView(bar) } }
    fun frame(): Rect = Rect(selectorParams.x, selectorParams.y, selectorParams.x + selectorParams.width, selectorParams.y + selectorParams.height)

    private fun cycleShape(button: TextView) {
        shape = when (shape) { Shape.RECTANGLE -> Shape.SQUARE; Shape.SQUARE -> Shape.CIRCLE; Shape.CIRCLE -> Shape.RECTANGLE }
        button.text = "Shape: ${shape.name.lowercase().replaceFirstChar { it.uppercase() }}"
        if (shape == Shape.SQUARE) selectorParams.height = selectorParams.width
        wm.updateViewLayout(selector, selectorParams); selector.invalidate()
    }

    private fun button(label: String, action: () -> Unit) = TextView(context).apply {
        text = label; textSize = 12f; setTextColor(Color.WHITE); setPadding(12, 8, 12, 8); setOnClickListener { action() }
    }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private inner class SelectorView(ctx: Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC315EFB.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f }
        private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0; private var resizing = false
        override fun onDraw(c: android.graphics.Canvas) {
            super.onDraw(c)
            if (shape == Shape.CIRCLE) c.drawOval(3f, 3f, width - 3f, height - 3f, paint)
            else c.drawRect(3f, 3f, width - 3f, height - 3f, paint)
            c.drawCircle(width - 16f, height - 16f, 9f, paint)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; startX=selectorParams.x; startY=selectorParams.y; resizing=e.x>width-50 && e.y>height-50; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(e.rawX-downX).toInt(); val dy=(e.rawY-downY).toInt()
                    if (resizing) { selectorParams.width=(320+dx).coerceAtLeast(120); selectorParams.height=(180+dy).coerceAtLeast(80) }
                    else { selectorParams.x=startX+dx; selectorParams.y=startY+dy; barParams.x=selectorParams.x; barParams.y=selectorParams.y-70 }
                    wm.updateViewLayout(this, selectorParams); if(!resizing) wm.updateViewLayout(bar, barParams); invalidate(); return true
                }
            }; return true
        }
    }
}
