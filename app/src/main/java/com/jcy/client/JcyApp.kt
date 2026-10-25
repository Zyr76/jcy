package com.jcy.client

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.jcy.client.net.Bootstrap

class JcyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        runCatching { Bootstrap.initNativeSdk() }
    }

    companion object {
        lateinit var instance: JcyApp
            private set

        fun deviceId(context: Context): String {
            val sp = context.getSharedPreferences("jcy", Context.MODE_PRIVATE)
            sp.getString("device_id", null)?.let { return it }
            val androidId = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()
            val id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                // 官方 deviceId 是 64 位 hex，这里用 sha256(ANDROID_ID) 保持稳定且形态一致
                val d = java.security.MessageDigest.getInstance("SHA-256").digest(androidId!!.toByteArray())
                val sb = StringBuilder(); for (x in d) sb.append(String.format("%02x", x.toInt() and 0xFF)); sb.toString()
            } else {
                // 官方 deviceId 为 64 位 hex（sha256 形态）
                val b = ByteArray(32); java.security.SecureRandom().nextBytes(b)
                val sb = StringBuilder(); for (x in b) sb.append(String.format("%02x", x.toInt() and 0xFF))
                sb.toString()
            }
            sp.edit().putString("device_id", id).apply()
            return id
        }
    }
}
