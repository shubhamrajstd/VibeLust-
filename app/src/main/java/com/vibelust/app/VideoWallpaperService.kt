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

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                mediaPlayer?.start()
            } else {
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.d("VideoWallpaperService", "onSurfaceCreated")
            val wallpaperFile = File(filesDir, "live_wallpaper.mp4")
            if (!wallpaperFile.exists()) {
                Log.e("VideoWallpaperService", "No wallpaper file found at: ${wallpaperFile.absolutePath}")
                return
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(wallpaperFile.absolutePath)
                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("VideoWallpaperService", "Error setting up MediaPlayer", e)
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
