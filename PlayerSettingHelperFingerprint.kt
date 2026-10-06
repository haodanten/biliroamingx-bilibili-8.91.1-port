package app.revanced.patches.bilibili.video.quality.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

/**
 * 定位"取默认画质"的那个方法。
 *
 * 8.91.1 适配: 旧锚点是 `["PlayerSettingHelper", "get free data failed"]`。
 * `"get free data failed"` 这条日志在新宿主里已被删除, 于是指纹失效。
 *
 * 新目标 (全库唯一同时含这两条字符串的方法):
 *   `Lai6/h;->c()I` —— public static、无参、返回 I,
 *   内部日志为 `"quality settings:" ... " defaultQuality:" ... "PlayerSettingHelper"`,
 *   形状与旧指纹的 `returnType = "I"` 一致。
 */
object PlayerSettingHelperFingerprint : MethodFingerprint(
    returnType = "I",
    strings = listOf("PlayerSettingHelper", "quality settings:")
)
