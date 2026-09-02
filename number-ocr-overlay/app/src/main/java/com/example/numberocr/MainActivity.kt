package com.example.numberocr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.numberocr.capture.ScreenCaptureService
import com.example.numberocr.results.ResultsActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private val mediaProjectionRequest = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.startFloatingButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Enable overlay permission, then tap Start Floating Mode again.", Toast.LENGTH_LONG).show()
            } else {
                startActivityForResult(
                    getSystemService(android.media.projection.MediaProjectionManager::class.java)
                        .createScreenCaptureIntent(), mediaProjectionRequest
                )
            }
        }
        findViewById<MaterialButton>(R.id.viewResultsButton).setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
    }

    @Deprecated("Activity result API kept simple for this sample")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == mediaProjectionRequest && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "Floating selector started. Resize it over the digits.", Toast.LENGTH_LONG).show()
        }
    }
}
