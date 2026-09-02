package com.example.numberocr.results

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.numberocr.R
import com.google.android.material.button.MaterialButton

class ResultsActivity : AppCompatActivity() {
    private val createTextFile = 9100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        render()
        findViewById<MaterialButton>(R.id.copyAllButton).setOnClickListener {
            val text = DetectionRepository.asText()
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Detected codes", text))
            Toast.makeText(this, "Codes copied to clipboard.", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TITLE, "detected_codes.txt")
            }, createTextFile)
        }
        findViewById<MaterialButton>(R.id.clearButton).setOnClickListener { DetectionRepository.clear(); render() }
    }
    private fun render() {
        val rows = DetectionRepository.snapshot().map { "${it.displayTime()}    ${it.code}" }
        findViewById<ListView>(R.id.resultsList).adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == createTextFile && resultCode == RESULT_OK && data?.data != null) {
            contentResolver.openOutputStream(data.data!!)?.bufferedWriter()?.use { it.write(DetectionRepository.asText()) }
            Toast.makeText(this, "TXT file exported.", Toast.LENGTH_SHORT).show()
        }
    }
}
