package com.jcy.client

import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.jcy.client.net.BannerItem
import com.jcy.client.net.BannerRepository
import com.jcy.client.net.HomeRepository
import com.jcy.client.net.Module
import com.jcy.client.net.VideoItem
import com.jcy.client.ui.Ui
import com.jcy.client.ui.Ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页：底部导航（主页 / 我的）。
 * 主页 = 轮播图 + 分类模块（横向）+ 猜你喜欢（网格）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var tabHome: LinearLayout
    private lateinit var tabMine: LinearLayout

    private var homeView: View? = null
    private var mineView: View? = null
    private val autoScroll = Handler(Looper.getMainLooper())
    private var pager: ViewPager2? = null
    private var pagerCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor(Ui.BG)) }
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // 底部导航
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0E1218"))
            layoutParams = FrameLayout.LayoutParams(-1, dp(this@MainActivity, 56), Gravity.BOTTOM)
        }
        tabHome = navTab("主页", true) { switchTab(0) }
        tabMine = navTab("我的", false) { switchTab(1) }
        nav.addView(tabHome, LinearLayout.LayoutParams(0, -1, 1f))
        nav.addView(tabMine, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(nav)
        // 内容避让导航
        (content.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = dp(this, 56)

        setContentView(root)
        switchTab(0)
    }

    private fun navTab(label: String, active: Boolean, onClick: () -> Unit): LinearLayout {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val t = Ui.tv(ctx, label, 14f, if (active) Ui.ACCENT else Ui.SUB, bold = active)
            addView(t)
            tag = t
            setOnClickListener { onClick() }
        }
    }

    private fun switchTab(index: Int) {
        setTabActive(tabHome, index == 0)
        setTabActive(tabMine, index == 1)
        content.removeAllViews()
        val v = if (index == 0) {
            homeView ?: buildHome().also { homeView = it }
        } else {
            mineView ?: buildMine().also { mineView = it }
        }
        content.addView(v, FrameLayout.LayoutParams(-1, -1))
        if (index != 0) { autoScroll.removeCallbacksAndMessages(null) }
    }

    private fun setTabActive(tab: LinearLayout, active: Boolean) {
        val t = tab.tag as? TextView ?: return
        t.setTextColor(Color.parseColor(if (active) Ui.ACCENT else Ui.SUB))
        t.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    // ---------------- 首页 ----------------
    private fun buildHome(): View {
        val ctx = this
        val scroll = ScrollView(ctx).apply { isFillViewport = true }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(ctx, 12), 0, dp(ctx, 24))
        }
        scroll.addView(col)

        col.addView(Ui.tv(ctx, "囧次元", 24f, Ui.TEXT, true).apply {
            setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 12))
        })

        val status = Ui.tv(ctx, "正在加载…", 13f, Ui.SUB).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 24), 0, dp(ctx, 24))
        }
        val loading = ProgressBar(ctx).apply { visibility = View.GONE }
        col.addView(status)
        col.addView(loading)

        loadHome(col, status, loading)
        return scroll
    }

    private fun loadHome(col: LinearLayout, status: TextView, loading: ProgressBar) {
        val ctx = this
        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val deviceId = JcyApp.deviceId(this@MainActivity)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Triple(
                        BannerRepository.list(deviceId),
                        HomeRepository.videoModules(deviceId),
                        HomeRepository.recommend(deviceId),
                    )
                }
            }
            loading.visibility = View.GONE
            result.onSuccess { (banners, modules, recommend) ->
                status.visibility = View.GONE
                if (banners.isNotEmpty()) col.addView(buildCarousel(banners), 0)
                modules.forEach { m -> if (m.items.isNotEmpty()) col.addView(buildModule(m)) }
                if (recommend.isNotEmpty()) col.addView(buildRecommend(recommend))
                if (banners.isEmpty() && modules.isEmpty() && recommend.isEmpty()) {
                    status.visibility = View.VISIBLE
                    status.text = "暂无数据"
                }
            }.onFailure { e ->
                status.visibility = View.VISIBLE
                status.text = "加载失败：${e.message}"
            }
        }
    }

    /** 轮播图（ViewPager2 + 指示点 + 自动滚动）。 */
    private fun buildCarousel(banners: List<BannerItem>): View {
        val ctx = this
        val wrap = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(ctx, 172)).apply {
                setMargins(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 12))
            }
        }
        val vp = ViewPager2(ctx).apply {
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val iv = ImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    return object : RecyclerView.ViewHolder(iv) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val b = banners[position]
                    val iv = holder.itemView as ImageView
                    iv.load(b.imageUrl)
                    iv.setOnClickListener {
                        startActivity(Intent(ctx, PlayerActivity::class.java)
                            .putExtra("videoId", b.videoId).putExtra("title", b.title))
                    }
                }
                override fun getItemCount() = banners.size
            }
            setBackgroundColor(Color.parseColor(Ui.CARD))
        }
        // 圆角
        val clip = FrameLayout(ctx).apply {
            background = Ui.round(Ui.CARD, 12, ctx)
            clipToOutline = true
        }
        clip.addView(vp, FrameLayout.LayoutParams(-1, -1))
        wrap.addView(clip, FrameLayout.LayoutParams(-1, -1))

        // 指示点
        val dots = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in banners.indices) {
            dots.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 6), dp(ctx, 6)).apply {
                    setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0)
                }
                background = Ui.round(if (i == 0) Ui.ACCENT else "#55FFFFFF", 3, ctx)
            })
        }
        wrap.addView(dots, FrameLayout.LayoutParams(-1, dp(ctx, 12), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(ctx, 8)
        })

        vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dots.childCount) {
                    dots.getChildAt(i).background =
                        Ui.round(if (i == position) Ui.ACCENT else "#55FFFFFF", 3, ctx)
                }
            }
        })

        pager = vp
        pagerCount = banners.size
        autoScroll.removeCallbacksAndMessages(null)
        autoScroll.postDelayed(object : Runnable {
            override fun run() {
                if (pagerCount > 1) {
                    val next = ((pager?.currentItem ?: 0) + 1) % pagerCount
                    pager?.setCurrentItem(next, true)
                    autoScroll.postDelayed(this, 4000)
                }
            }
        }, 4000)
        return wrap
    }

    /** 分类模块：标题 + 横向滚动海报。 */
    private fun buildModule(m: Module): View {
        val ctx = this
        val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 8))
        }
        head.addView(Ui.tv(ctx, m.name.ifEmpty { "推荐" }, 16f, Ui.TEXT, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.tv(ctx, m.items.size.toString() + " 部", 12f, Ui.SUB))
        wrap.addView(head)

        val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 16), 0, dp(ctx, 6), 0)
        }
        m.items.forEach { it2 ->
            row.addView(Ui.posterCard(ctx, it2.name, it2.cover, 96) { openVideo(it2) })
        }
        hs.addView(row)
        wrap.addView(hs)
        return wrap
    }

    /** 猜你喜欢：3 列网格。 */
    private fun buildRecommend(items: List<VideoItem>): View {
        val ctx = this
        val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 8))
            addView(Ui.tv(ctx, "猜你喜欢", 16f, Ui.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        })
        val rv = RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, 3)
            adapter = RecommendAdapter(items) { openVideo(it) }
            isNestedScrollingEnabled = false
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
        }
        wrap.addView(rv, LinearLayout.LayoutParams(-1, -2))
        return wrap
    }

    private fun openVideo(item: VideoItem) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("videoId", item.id).putExtra("title", item.name))
    }

    // ---------------- 我的 ----------------
    private fun buildMine(): View {
        val ctx = this
        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 36), dp(ctx, 16), dp(ctx, 24))
        }
        scroll.addView(col)

        val user = Session.user
        // 用户卡
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(Ui.CARD, 14, ctx)
            setPadding(dp(ctx, 16), dp(ctx, 18), dp(ctx, 16), dp(ctx, 18))
        }
        val avatar = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 56), dp(ctx, 56))
            background = Ui.round("#2A3344", 28, ctx)
            clipToOutline = true
            if (user?.avatar?.isNotEmpty() == true) load(user.avatar)
        }
        card.addView(avatar)
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), 0, 0, 0)
        }
        if (user == null) {
            info.addView(Ui.tv(ctx, "未登录", 17f, Ui.TEXT, true))
            info.addView(Ui.tv(ctx, "登录后可同步收藏与观看记录", 12f, Ui.SUB))
        } else {
            info.addView(Ui.tv(ctx, user.name.ifEmpty { "用户" }, 17f, Ui.TEXT, true))
            info.addView(Ui.tv(ctx, user.email.ifEmpty { "" }, 12f, Ui.SUB))
        }
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val action = Ui.tv(ctx, if (user == null) "登录/注册" else "退出", 13f,
            if (user == null) Ui.ACCENT else Ui.SUB, true).apply {
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8))
            setOnClickListener {
                if (user == null) LoginDialog.show(this@MainActivity) { rebuildMine() }
                else { Session.logout(); rebuildMine() }
            }
        }
        card.addView(action)
        col.addView(card)

        // 菜单
        listOf("我的收藏" to "暂未开放", "观看历史" to "暂未开放", "设置" to "v1.0.0").forEach { (k, v) ->
            col.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.round(Ui.CARD, 12, ctx)
                setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(ctx, 12)
                addView(Ui.tv(ctx, k, 14f, Ui.TEXT), LinearLayout.LayoutParams(0, -2, 1f))
                addView(Ui.tv(ctx, v, 12f, Ui.SUB))
            })
        }
        return scroll
    }

    private fun rebuildMine() {
        mineView = buildMine()
        content.removeAllViews()
        content.addView(mineView, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onDestroy() {
        autoScroll.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** 猜你喜欢网格适配器。 */
    class RecommendAdapter(
        private val items: List<VideoItem>,
        private val onClick: (VideoItem) -> Unit,
    ) : RecyclerView.Adapter<RecommendAdapter.VH>() {
        class VH(val col: LinearLayout, val img: ImageView, val title: TextView) : RecyclerView.ViewHolder(col)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(-1, -2).apply {
                    setMargins(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 12))
                }
            }
            val img = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(ctx, 150))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Ui.round(Ui.CARD, 10, ctx)
                clipToOutline = true
            }
            val title = Ui.tv(ctx, "", 12f, Ui.TEXT).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(ctx, 6), 0, 0)
            }
            col.addView(img)
            col.addView(title)
            return VH(col, img, title)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it2 = items[position]
            holder.title.text = it2.name
            holder.img.load(it2.cover)
            holder.col.setOnClickListener { onClick(it2) }
        }

        override fun getItemCount() = items.size
    }
}
