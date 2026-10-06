package app.revanced.bilibili.patches.okhttp.hooks

import app.revanced.bilibili.patches.json.PegasusPatch
import app.revanced.bilibili.patches.okhttp.ApiHook
import app.revanced.bilibili.utils.Utils
import app.revanced.bilibili.utils.Versions
import app.revanced.bilibili.utils.toJSONObject

object FeedIndex : ApiHook() {
    // 尊重官方，不强制关闭AB实验
    private val newPegasusEnabled by lazy {
        Utils.getAb("ff_key_use_new_pegasus", false)
    }

    /** 只匹配 feed/index 这个路径本身 (允许结尾多一个 '/' 或省略), 不放宽到同前缀路径。 */
    private val feedIndexPath = Regex("/x/v2/feed/index/?$")

    private fun isFeedIndexUrl(url: String): Boolean =
        feedIndexPath.containsMatchIn(url.substringBefore('?'))

    override fun shouldHook(url: String, status: Int): Boolean {
        // Fix #828: 返回手势刷新主页时 URL 可能不带查询参数, 旧实现要求结尾紧跟 '?'
        // 会漏匹配。改为按路径匹配 (带不带 query 都命中), 同时把
        // "/x/v2/feed/indexXXX" 这类同前缀路径排除在外。
        return status.isOk && Versions.ge7_76_0() && isFeedIndexUrl(url) && newPegasusEnabled
    }

    override fun hook(url: String, status: Int, request: String, response: String): String {
        val resp = response.toJSONObject()
        if (resp.optInt("code", -1) == 0) {
            val data = resp.optJSONObject("data")
            if (data != null)
                PegasusPatch.pegasusHook(data)
            return resp.toString()
        }
        return response
    }
}
