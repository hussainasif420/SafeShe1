package com.example.safeshe2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import java.io.FileOutputStream

class LawPath : AppCompatActivity() {
    private lateinit var pdfView: PDFView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawpath)

        pdfView = findViewById(R.id.pdfView)

        // Load PDF from assets
        try {
            // Check if the file exists in assets
            val assetManager = assets
            val fileExists = try {
                assetManager.open("law_guide.pdf")
                true
            } catch (e: Exception) {
                false
            }

            if (fileExists) {
                // Get the PDF file from assets
                val inputStream = assets.open("law_guide.pdf")
                
                // Create a temporary file
                val tempFile = File(cacheDir, "temp_law_guide.pdf")
                tempFile.createNewFile()
                
                // Copy the PDF to the temporary file
                val outputStream = FileOutputStream(tempFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                
                // Load the PDF from the temporary file
                pdfView.fromFile(tempFile)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .enableAnnotationRendering(false)
                    .password(null)
                    .scrollHandle(null)
                    .enableAntialiasing(true)
                    .spacing(0)
                    .load()
            } else {
                Toast.makeText(this, "PDF file not found in assets folder", Toast.LENGTH_LONG).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            finish()
        }
    }
}