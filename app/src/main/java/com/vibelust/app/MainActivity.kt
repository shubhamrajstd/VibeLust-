package com.vibelust.app

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import com.google.android.gms.ads.*

class MainActivity : ComponentActivity() {

    private var progressDialog: AlertDialog? = null
    private var isProcessingFinished = false

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.d("MainActivity", "Selected URI: $uri")
            isProcessingFinished = false
            showProcessingDialog()
            processVideoNatively(uri)
        } else {
            Log.d("MainActivity", "No media selected")
            Toast.makeText(this, "No video selected.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdMob Mobile Ads SDK
        MobileAds.initialize(this) { status ->
            Log.i("MainActivity", "AdMob initialized: $status")
        }

        // Set up Banner Ad View
        val adView = findViewById<AdView>(R.id.adView)
        if (adView != null) {
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        }

        // Set up Video selection trigger button
        findViewById<Button>(R.id.btnPickVideo)?.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }

    private fun checkAndLaunchWallpaper() {
        if (!isProcessingFinished) return

        progressDialog?.dismiss()

        // Open live wallpaper selection system screen directly focusing our service package/class
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, VideoWallpaperService::class.java)
                )
            }
            startActivity(intent)
            Toast.makeText(this, "Excellent! Click 'Apply/Set Wallpaper' to activate your premium live video loop.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // General Fallback
            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
            try {
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Could not open wallpaper manager. Please set it manually in settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showProcessingDialog() {
        if (progressDialog == null) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(40, 50, 40, 50)
                setBackgroundColor(0xFF1A1A1A.toInt())
            }

            val progressBar = ProgressBar(this).apply {
                isIndeterminate = true
            }

            val textView = TextView(this).apply {
                text = "Processing Wallpaper...\nTailoring to fluid loop"
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(10, 24, 10, 10)
            }

            container.addView(progressBar)
            container.addView(textView)

            progressDialog = AlertDialog.Builder(this)
                .setView(container)
                .setCancelable(false)
                .create()
        }
        progressDialog?.show()
    }

    private fun processVideoNatively(uri: Uri) {
        kotlin.concurrent.thread(name = "VideoProcessingThread") {
            try {
                val finalWallpaperFile = File(filesDir, "live_wallpaper.mp4")
                if (finalWallpaperFile.exists()) {
                    finalWallpaperFile.delete()
                }
                var success = false
                var extractor: MediaExtractor? = null
                var muxer: MediaMuxer? = null

                try {
                    extractor = MediaExtractor()
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    }

                    val trackCount = extractor.trackCount
                    var videoTrackIndexInExtractor = -1
                    var videoFormat: MediaFormat? = null

                    // Step 1: Find the video track & ignore/remove audio
                    for (i in 0 until trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("video/")) {
                            videoTrackIndexInExtractor = i
                            videoFormat = format
                            break
                        }
                    }

                    if (videoTrackIndexInExtractor != -1 && videoFormat != null) {
                        extractor.selectTrack(videoTrackIndexInExtractor)

                        // Step 2: Set up native MediaMuxer
                        muxer = MediaMuxer(finalWallpaperFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        val videoTrackIndexInMuxer = muxer.addTrack(videoFormat)
                        muxer.start()

                        val maxBufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        } else {
                            1024 * 1024
                        }
                        val buffer = ByteBuffer.allocate(maxBufferSize)
                        val bufferInfo = MediaCodec.BufferInfo()

                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                        // Step 3: Parse and copy first 5 seconds (5,000,000 Us)
                        val trimDurationUs = 5_000_000L

                        while (true) {
                            bufferInfo.offset = 0
                            bufferInfo.size = extractor.readSampleData(buffer, 0)
                            if (bufferInfo.size < 0) {
                                break
                            }

                            bufferInfo.presentationTimeUs = extractor.sampleTime
                            if (bufferInfo.presentationTimeUs > trimDurationUs) {
                                break
                            }

                            bufferInfo.flags = extractor.sampleFlags

                            muxer.writeSampleData(videoTrackIndexInMuxer, buffer, bufferInfo)
                            extractor.advance()
                        }
                        success = true
                        Log.i("MainActivity", "Native video trim and mute successful.")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Native multiplexer failed, falling back to clean stream copy.", e)
                } finally {
                    try {
                        extractor?.release()
                    } catch (ex: Exception) {}
                    try {
                        muxer?.stop()
                    } catch (ex: Exception) {}
                    try {
                        muxer?.release()
                    } catch (ex: Exception) {}
                }

                // Fallback: If native muxer failed, copy original selecting file directly to prevent any issues
                if (!success) {
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            finalWallpaperFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Log.i("MainActivity", "Video copied via clean backup stream successfully.")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Both native extraction and backup stream failed", e)
                        runOnUiThread {
                            progressDialog?.dismiss()
                            Toast.makeText(this@MainActivity, "Failed to process selected video: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                        return@thread
                    }
                }

                runOnUiThread {
                    isProcessingFinished = true
                    checkAndLaunchWallpaper()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing video file", e)
                runOnUiThread {
                    progressDialog?.dismiss()
                    Toast.makeText(this, "Error accessing selected video: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
