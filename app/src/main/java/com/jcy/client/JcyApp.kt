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
                androidId!!
            } else {
                java.util.UUID.randomUUID().toString().replace("-", "")
            }
            sp.edit().putString("device_id", id).apply()
            return id
        }
    }
}
