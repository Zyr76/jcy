package com.jcy.client

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jcy.client.net.CommentItem
import com.jcy.client.net.CommunityRepository
import com.jcy.client.net.DanmakuItem
import com.jcy.client.net.HomeRepository
import com.jcy.client.net.ResolveRepository
import com.jcy.client.net.VideoDetail
import com.jcy.client.ui.DanmakuView
import com.jcy.client.ui.Ui
import com.jcy.client.ui.Ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放页：播放器 + 弹幕层 + 三 Tab（详情 / 讨论 / 影评）。
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var danmakuView: DanmakuView
    private lateinit var titleBar: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var tabContent: FrameLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    private var videoId = ""
    private var title = ""
    private var detail: VideoDetail? = null
    private var episodes = listOf<ResolveRepository.Episode>()
    private var selectedEpisode = 1
    private var currentCode = "mp4"
    private var comments = listOf<CommentItem>()

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            danmakuView.setTime(player?.currentPosition ?: 0L)
            ticker.postDelayed(this, 60)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = intent.getStringExtra("videoId").orEmpty()
        title = intent.getStringExtra("title").orEmpty()

        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Ui.BG))
        }

        // 播放器区（16:9）+ 弹幕覆盖 + 顶部返回
        val playerWrap = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0).apply { weight = 0f }
        }
        playerView = PlayerView(ctx).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        danmakuView = DanmakuView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        titleBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 4), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
            addView(Ui.tv(ctx, "‹", 26f, "#FFFFFF", true).apply {
                setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
                setOnClickListener { finish() }
            })
            addView(Ui.tv(ctx, title, 15f, "#FFFFFF", true), LinearLayout.LayoutParams(0, -2, 1f))
            // 弹幕开关
            addView(Ui.tv(ctx, "弹", 13f, Ui.ACCENT, true).apply {
                setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
                setOnClickListener {
                    val on = !danmakuView.isDanmakuEnabled()
                    danmakuView.setDanmakuEnabled(on)
                    setTextColor(Color.parseColor(if (on) Ui.ACCENT else Ui.SUB))
                    Toast.makeText(ctx, if (on) "弹幕开" else "弹幕关", Toast.LENGTH_SHORT).show()
                }
            })
        }
        playerWrap.addView(playerView)
        playerWrap.addView(danmakuView)
        playerWrap.addView(titleBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // 播放器高度固定 16:9
        val w = resources.displayMetrics.widthPixels
        root.addView(playerWrap, LinearLayout.LayoutParams(-1, w * 9 / 16))

        // Tab 栏
        tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0E1218"))
            layoutParams = LinearLayout.LayoutParams(-1, dp(ctx, 44))
        }
        listOf("详情", "讨论", "影评").forEachIndexed { i, name ->
            val t = Ui.tv(ctx, name, 14f, if (i == 0) Ui.ACCENT else Ui.SUB, i == 0).apply {
                gravity = Gravity.CENTER
                setOnClickListener { selectTab(i) }
            }
            t.tag = i
            tabRow.addView(t, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(tabRow)

        // Tab 内容
        tabContent = FrameLayout(ctx)
        root.addView(tabContent, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })

        status = Ui.tv(ctx, "正在解析播放地址…", 13f, Ui.SUB).apply { gravity = Gravity.CENTER }
        progress = ProgressBar(ctx)
        val overlay = FrameLayout(ctx).apply {
            addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            addView(progress, FrameLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44), Gravity.CENTER))
        }
        tabContent.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        tabContent.tag = overlay

        setContentView(root)
        if (videoId.isEmpty()) {
            status.text = "缺少 videoId"
            progress.visibility = View.GONE
            return
        }
        loadDetail()
    }

    // ---------------- 数据加载 ----------------
    private fun loadDetail() {
        lifecycleScope.launch {
            val deviceId = JcyApp.deviceId(this@PlayerActivity)
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val d = HomeRepository.videoDetail(deviceId, videoId)
                    val code = d.players.firstOrNull()?.first ?: "mp4"
                    val eps = ResolveRepository.episodes(deviceId, videoId, code)
                    Triple(d, code, eps)
                }
            }
            r.onSuccess { (d, code, eps) ->
                detail = d
                currentCode = code
                episodes = eps
                title = d.name.ifEmpty { title }
                (titleBar.getChildAt(1) as? TextView)?.text = title
                selectTab(0)
                if (eps.isNotEmpty()) playEpisode(eps.first().num)
                else status.text = "暂无剧集"
            }.onFailure { e ->
                status.text = "加载失败：${e.message}"
                progress.visibility = View.GONE
            }
        }
    }

    private fun playEpisode(num: Int) {
        selectedEpisode = num
        progress.visibility = View.VISIBLE
        status.visibility = View.VISIBLE
        status.text = "正在解析第 $num 集…"
        lifecycleScope.launch {
            val deviceId = JcyApp.deviceId(this@PlayerActivity)
            val r = withContext(Dispatchers.IO) {
                runCatching { ResolveRepository.resolvePlayUrl(deviceId, videoId, currentCode, num) }
            }
            progress.visibility = View.GONE
            r.onSuccess { (url, msg) ->
                if (url == null) {
                    status.text = "解析失败：$msg"
                } else {
                    status.visibility = View.GONE
                    play(url)
                    loadDanmaku(num)
                }
            }.onFailure { e -> status.text = "解析失败：${e.message}" }
        }
    }

    private fun play(url: String) {
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
        }
        if (!ticker.hasCallbacks(tick)) ticker.post(tick)
    }

    private fun loadDanmaku(num: Int) {
        lifecycleScope.launch {
            val deviceId = JcyApp.deviceId(this@PlayerActivity)
            val r = withContext(Dispatchers.IO) {
                runCatching { CommunityRepository.danmaku(deviceId, videoId, num) }
            }
            r.onSuccess { list -> danmakuView.setItems(list) }
        }
    }

    private fun loadComments() {
        status.text = "加载讨论…"
        lifecycleScope.launch {
            val deviceId = JcyApp.deviceId(this@PlayerActivity)
            val r = withContext(Dispatchers.IO) {
                runCatching { CommunityRepository.comments(deviceId, videoId) }
            }
            r.onSuccess { list ->
                comments = list
                selectTab(1)
            }.onFailure { e ->
                status.text = "讨论加载失败：${e.message}"
            }
        }
    }

    // ---------------- Tab ----------------
    private fun selectTab(index: Int) {
        for (i in 0 until tabRow.childCount) {
            val t = tabRow.getChildAt(i) as? TextView ?: continue
            t.setTextColor(Color.parseColor(if (i == index) Ui.ACCENT else Ui.SUB))
            t.setTypeface(null, if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        // 清理（保留 overlay）
        val overlay = tabContent.tag as? View
        tabContent.removeAllViews()
        if (overlay != null && (status.visibility == View.VISIBLE || progress.visibility == View.VISIBLE)) {
            tabContent.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }
        when (index) {
            0 -> tabContent.addView(buildDetailTab(), FrameLayout.LayoutParams(-1, -1))
            1 -> {
                if (comments.isEmpty()) { loadComments(); return }
                tabContent.addView(buildCommentsTab(), FrameLayout.LayoutParams(-1, -1))
            }
            else -> tabContent.addView(buildReviewsTab(), FrameLayout.LayoutParams(-1, -1))
        }
    }

    /** 详情：简介 + 剧集网格 + 线路。 */
    private fun buildDetailTab(): View {
        val ctx = this
        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 24))
        }
        scroll.addView(col)

        val d = detail
        if (d != null) {
            // 头部信息
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val cover = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 92), dp(ctx, 122))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Ui.round(Ui.CARD, 8, ctx)
                clipToOutline = true
                if (d.cover.isNotEmpty()) load(d.cover)
            }
            head.addView(cover)
            val info = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 12), 0, 0, 0)
            }
            info.addView(Ui.tv(ctx, d.name, 16f, Ui.TEXT, true))
            if (d.players.isNotEmpty()) {
                info.addView(Ui.tv(ctx, "线路：" + d.players.joinToString(" / ") { it.second },
                    12f, Ui.SUB).apply { setPadding(0, dp(ctx, 6), 0, 0) })
            }
            info.addView(Ui.tv(ctx, "共 " + episodes.size + " 集", 12f, Ui.SUB).apply {
                setPadding(0, dp(ctx, 6), 0, 0)
            })
            head.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            col.addView(head)

            if (d.description.isNotEmpty()) {
                col.addView(Ui.tv(ctx, "简介", 15f, Ui.TEXT, true).apply {
                    setPadding(0, dp(ctx, 16), 0, dp(ctx, 6))
                })
                col.addView(Ui.tv(ctx, d.description, 13f, Ui.SUB))
            }
        }

        // 剧集
        if (episodes.isNotEmpty()) {
            col.addView(Ui.tv(ctx, "选集", 15f, Ui.TEXT, true).apply {
                setPadding(0, dp(ctx, 16), 0, dp(ctx, 8))
            })
            val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            episodes.forEach { ep ->
                val active = ep.num == selectedEpisode
                row.addView(Ui.tv(ctx, ep.name.ifEmpty { ep.num.toString() }, 13f,
                    if (active) "#FFFFFF" else Ui.TEXT, active).apply {
                    gravity = Gravity.CENTER
                    background = Ui.round(if (active) Ui.ACCENT else Ui.CARD, 8, ctx)
                    setPadding(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9))
                    (layoutParams as? LinearLayout.LayoutParams)?.marginEnd = dp(ctx, 8)
                    setOnClickListener {
                        if (ep.num != selectedEpisode) {
                            playEpisode(ep.num)
                            selectTab(0)
                        }
                    }
                })
            }
            hs.addView(row)
            col.addView(hs)
        }
        return scroll
    }

    /** 讨论：评论列表。 */
    private fun buildCommentsTab(): View {
        val ctx = this
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = CommentAdapter(comments)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 16))
        }
        return rv
    }

    /** 影评：暂用占位（接口需 GET + 独立加密通道，待适配）。 */
    private fun buildReviewsTab(): View {
        val ctx = this
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 24), dp(ctx, 48), dp(ctx, 24), dp(ctx, 24))
        }
        col.addView(Ui.tv(ctx, "影评", 16f, Ui.TEXT, true))
        col.addView(Ui.tv(ctx, "影评数据接口暂未适配", 13f, Ui.SUB).apply {
            setPadding(0, dp(ctx, 10), 0, 0)
        })
        return col
    }

    override fun onStop() {
        super.onStop(); player?.pause()
    }

    override fun onDestroy() {
        ticker.removeCallbacksAndMessages(null)
        player?.release(); player = null
        super.onDestroy()
    }

    /** 评论适配器。 */
    class CommentAdapter(private val items: List<CommentItem>) : RecyclerView.Adapter<CommentAdapter.VH>() {
        class VH(val col: LinearLayout) : RecyclerView.ViewHolder(col)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            }
            return VH(col)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ctx = holder.itemView.context
            val c = items[position]
            val col = holder.col
            col.removeAllViews()
            val avatar = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36))
                background = Ui.round("#2A3344", 18, ctx)
                clipToOutline = true
                if (c.avatar.isNotEmpty()) load(c.avatar)
            }
            col.addView(avatar)
            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 10), 0, 0, 0)
            }
            val nameRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            nameRow.addView(Ui.tv(ctx, c.nickname.ifEmpty { "用户" }, 13f, "#7FB3FF", true))
            if (c.isTop) {
                nameRow.addView(Ui.tv(ctx, " 置顶", 10f, Ui.ACCENT).apply { setPadding(dp(ctx, 6), 0, 0, 0) })
            }
            body.addView(nameRow)
            body.addView(Ui.tv(ctx, c.content, 13f, Ui.TEXT).apply { setPadding(0, dp(ctx, 5), 0, dp(ctx, 5)) })
            body.addView(Ui.tv(ctx, "👍 ${c.likeCount}   💬 ${c.replyCount}", 11f, Ui.SUB))
            col.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        }

        override fun getItemCount() = items.size
    }
}
