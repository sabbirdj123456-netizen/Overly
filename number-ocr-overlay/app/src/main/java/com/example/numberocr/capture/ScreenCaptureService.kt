package com.example.numberocr.capture

import android.app.*
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.numberocr.R
import com.example.numberocr.overlay.OverlayController
import com.example.numberocr.results.DetectionRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : LifecycleService() {
    companion object {
        const val ACTION_START = "com.example.numberocr.START"
        const val ACTION_STOP = "com.example.numberocr.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL = "ocr_capture"
        private const val NOTIFICATION_ID = 41
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recording = AtomicBoolean(false)
    private lateinit var overlay: OverlayController
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captureJob: Job? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_search).setContentTitle("Number OCR Overlay")
            .setContentText("Floating selector is ready").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
        if (!::overlay.isInitialized) overlay = OverlayController(this, { recording.set(true) }, { stopCapture() })
        if (projection == null) {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val resultData = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
            projection = mgr.getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED), resultData!!)
            val dm = resources.displayMetrics
            reader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection!!.createVirtualDisplay("NumberOCR", dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, Handler(Looper.getMainLooper()))
            overlay.show()
            startSampling()
        }
    }

    private fun startSampling() {
        if (captureJob?.isActive == true) return
        captureJob = serviceScope.launch {
            while (isActive) {
                if (recording.get()) processLatestFrame()
                delay(1000L)
            }
        }
    }

    private suspend fun processLatestFrame() = withContext(Dispatchers.Default) {
        val image = reader?.acquireLatestImage() ?: return@withContext
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val full = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(buffer)
            val r = overlay.frame()
            val left = r.left.coerceIn(0, full.width - 1); val top = r.top.coerceIn(0, full.height - 1)
            val right = r.right.coerceIn(left + 1, full.width); val bottom = r.bottom.coerceIn(top + 1, full.height)
            val cropped = Bitmap.createBitmap(full, left, top, right-left, bottom-top)
            val processed = preprocess(cropped)
            recognize(processed)
            full.recycle(); cropped.recycle(); processed.recycle()
        } finally { image.close() }
    }

    private fun preprocess(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            2.2f,0f,0f,0f,-160f, 0f,2.2f,0f,0f,-160f, 0f,0f,2.2f,0f,-160f, 0f,0f,0f,1f,0f))) }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun recognize(bitmap: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { result ->
            val codes = Regex("\\d{2,}").findAll(result.text).map { it.value }.toList()
            codes.forEach { DetectionRepository.addIfNew(it) }
        }
    }

    private fun stopCapture() {
        recording.set(false); captureJob?.cancel(); captureJob = null
        if (::overlay.isInitialized) overlay.hide()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "OCR capture", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        stopCapture(); virtualDisplay?.release(); reader?.close(); projection?.stop(); recognizer.close(); serviceScope.cancel(); super.onDestroy()
    }
}
