package com.jcy.client

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jcy.client.net.HomeRepository
import com.jcy.client.net.VideoItem
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private val adapter = VideoAdapter { item ->
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("videoId", item.id).putExtra("title", item.name))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0E14")) }

        val header = TextView(this).apply {
            text = "囧次元"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(48, 64, 48, 24)
        }

        recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = this@MainActivity.adapter
            setPadding(24, 0, 24, 24)
            clipToPadding = false
        }

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#8899AA"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        progress = ProgressBar(this).apply { visibility = ProgressBar.GONE }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(progress, FrameLayout.LayoutParams(120, 120, Gravity.CENTER))
        setContentView(root)

        load()
    }

    private fun load() {
        progress.visibility = ProgressBar.VISIBLE
        status.text = "正在解析网关并初始化加密会话…"
        lifecycleScope.launch {
            val deviceId = JcyApp.deviceId(this@MainActivity)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val modules = HomeRepository.videoModules(deviceId)
                    if (modules.isNotEmpty()) modules.flatMap { it.items } else HomeRepository.recommend(deviceId)
                }
            }
            progress.visibility = ProgressBar.GONE
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    status.text = "接口返回空数据"
                } else {
                    status.visibility = android.view.View.GONE
                    adapter.submit(list)
                }
            }.onFailure { e ->
                status.text = "加载失败：${e.message}\n\n（首次需真机联网；加密握手依赖 native 库）"
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    class VideoAdapter(private val onClick: (VideoItem) -> Unit) : RecyclerView.Adapter<VideoAdapter.VH>() {
        private val data = mutableListOf<VideoItem>()

        fun submit(list: List<VideoItem>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        class VH(val container: LinearLayout, val cover: android.widget.ImageView, val title: TextView) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val cover = android.widget.ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#151A24"))
            }
            val title = TextView(ctx).apply {
                setTextColor(Color.parseColor("#E8EDF5"))
                textSize = 12f
                maxLines = 2
                setPadding(4, 12, 4, 18)
            }
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(12, 12, 12, 12) }
                addView(cover)
                addView(title)
            }
            return VH(col, cover, title)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = data[position]
            holder.title.text = item.name.ifEmpty { item.id }
            holder.cover.setImageDrawable(null)
            if (item.cover.isNotEmpty()) {
                holder.cover.load(item.cover)
            }
            holder.container.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = data.size
    }
}
