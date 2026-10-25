package com.jcy.client

import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.jcy.client.net.SecureApi
import com.jcy.client.ui.Ui
import com.jcy.client.ui.Ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 登录 / 注册对话框。 */
object LoginDialog {

    fun show(activity: AppCompatActivity, onSuccess: () -> Unit) {
        val ctx = activity
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 8))
        }
        val account = EditText(ctx).apply {
            hint = "邮箱 / 手机号"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.parseColor(Ui.TEXT))
            setHintTextColor(Color.parseColor(Ui.SUB))
            setSingleLine()
        }
        val password = EditText(ctx).apply {
            hint = "密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.parseColor(Ui.TEXT))
            setHintTextColor(Color.parseColor(Ui.SUB))
            setSingleLine()
        }
        val tip = Ui.tv(ctx, "", 12f, "#FF6B6B").apply { setPadding(0, dp(ctx, 6), 0, 0) }

        col.addView(account)
        col.addView(password)
        col.addView(tip)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("登录 / 注册")
            .setView(col)
            .setPositiveButton("登录", null)
            .setNeutralButton("注册", null)
            .setNegativeButton("取消", null)
            .create()

        fun busy(b: Boolean) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !b
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = !b
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = account.text.toString().trim()
                val p = password.text.toString()
                if (a.isEmpty() || p.isEmpty()) { tip.text = "请输入账号和密码"; return@setOnClickListener }
                busy(true); tip.text = "登录中…"
                activity.lifecycleScope.launch {
                    val deviceId = JcyApp.deviceId(ctx)
                    val r = withContext(Dispatchers.IO) { runCatching { SecureApi.login(a, p, deviceId) } }
                    busy(false)
                    r.onSuccess { res ->
                        if (res.ok && res.token != null && res.user != null) {
                            Session.save(ctx, res.token, res.user)
                            Toast.makeText(ctx, "欢迎回来，${res.user.name}", Toast.LENGTH_SHORT).show()
                            dialog.dismiss(); onSuccess()
                        } else {
                            tip.text = res.message ?: "登录失败"
                        }
                    }.onFailure { e -> tip.text = "登录失败：${e.message}" }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val a = account.text.toString().trim()
                val p = password.text.toString()
                if (a.isEmpty() || p.isEmpty()) { tip.text = "请输入账号和密码"; return@setOnClickListener }
                busy(true); tip.text = "注册中…"
                activity.lifecycleScope.launch {
                    val deviceId = JcyApp.deviceId(ctx)
                    val r = withContext(Dispatchers.IO) { runCatching { SecureApi.register(a, p, deviceId) } }
                    busy(false)
                    r.onSuccess { res -> tip.text = res.message ?: (if (res.ok) "注册成功，请点登录" else "注册失败") }
                        .onFailure { e -> tip.text = "注册失败：${e.message}" }
                }
            }
        }
        dialog.show()
    }
}
