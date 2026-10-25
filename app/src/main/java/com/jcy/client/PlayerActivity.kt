package com.jcy.client

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jcy.client.net.HomeRepository
import com.jcy.client.net.PlaySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra("videoId").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }
        status = TextView(this).apply {
            setTextColor(Color.parseColor("#CCD6E0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            text = "正在解析播放地址…"
        }
        progress = ProgressBar(this).apply { visibility = ProgressBar.VISIBLE }

        root.addView(playerView)
        root.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(progress, FrameLayout.LayoutParams(120, 120, Gravity.CENTER))
        setContentView(root)
        supportActionBar?.title = title

        load(videoId)
    }

    private fun load(videoId: String) {
        if (videoId.isEmpty()) {
            status.text = "缺少 videoId"
            progress.visibility = ProgressBar.GONE
            return
        }
        lifecycleScope.launch {
            val deviceId = JcyApp.deviceId(this@PlayerActivity)
            val result = withContext(Dispatchers.IO) {
                runCatching { HomeRepository.videoDetail(deviceId, videoId) }
            }
            progress.visibility = ProgressBar.GONE
            result.onSuccess { detail ->
                if (detail.sources.isEmpty()) {
                    status.text = "未解析到播放源\n（可能要求登录或加密链路未完成）"
                    return@onSuccess
                }
                status.visibility = android.view.View.GONE
                play(detail.sources.first())
            }.onFailure { e ->
                status.text = "解析失败：${e.message}"
            }
        }
    }

    private fun play(source: PlaySource) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView?.player = exo
            exo.setMediaItem(MediaItem.fromUri(source.url))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
