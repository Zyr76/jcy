package com.jcy.client

import android.content.Context

/** 登录态（token + 用户信息），持久化到 SharedPreferences。 */
object Session {
    data class User(val id: String, val name: String, val email: String, val avatar: String, val vipLevel: Int)

    @Volatile var token: String? = null
    @Volatile var user: User? = null

    private fun sp(ctx: Context) = ctx.getSharedPreferences("jcy_session", Context.MODE_PRIVATE)

    fun restore(ctx: Context) {
        val p = sp(ctx)
        token = p.getString("token", null)
        val name = p.getString("name", null)
        if (name != null) {
            user = User(
                id = p.getString("id", "").orEmpty(),
                name = name,
                email = p.getString("email", "").orEmpty(),
                avatar = p.getString("avatar", "").orEmpty(),
                vipLevel = p.getInt("vip", 0),
            )
        }
    }

    fun save(ctx: Context, token: String, u: User) {
        this.token = token
        this.user = u
        sp(ctx).edit()
            .putString("token", token)
            .putString("id", u.id)
            .putString("name", u.name)
            .putString("email", u.email)
            .putString("avatar", u.avatar)
            .putInt("vip", u.vipLevel)
            .apply()
    }

    fun logout() {
        token = null
        user = null
        runCatching { sp(JcyApp.instance).edit().clear().apply() }
    }
}
