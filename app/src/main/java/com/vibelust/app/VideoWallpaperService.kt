package com.vibelust.app

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.io.File

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var lastModified: Long = 0

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                val wallpaperFile = File(filesDir, "live_wallpaper.mp4")
                val currentModified = if (wallpaperFile.exists()) wallpaperFile.lastModified() else 0
                if (currentModified != lastModified) {
                    lastModified = currentModified
                    reloadMediaPlayer(surfaceHolder, wallpaperFile)
                } else {
                    mediaPlayer?.start()
                }
            } else {
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.d("VideoWallpaperService", "onSurfaceCreated")
            val wallpaperFile = File(filesDir, "live_wallpaper.mp4")
            lastModified = if (wallpaperFile.exists()) wallpaperFile.lastModified() else 0
            reloadMediaPlayer(holder, wallpaperFile)
        }

        private fun reloadMediaPlayer(holder: SurfaceHolder, file: File) {
            mediaPlayer?.let {
                try {
                    it.stop()
                    it.release()
                } catch (e: Exception) {}
            }
            mediaPlayer = null

            if (!file.exists()) {
                Log.e("VideoWallpaperService", "No wallpaper file found at: ${file.absolutePath}")
                return
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(file.absolutePath)
                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("VideoWallpaperService", "Error setting up / reloading MediaPlayer", e)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            Log.d("VideoWallpaperService", "onSurfaceDestroyed")
            mediaPlayer?.let {
                try {
                    it.stop()
                    it.release()
                } catch (e: Exception) {}
            }
            mediaPlayer = null
        }
    }
}
