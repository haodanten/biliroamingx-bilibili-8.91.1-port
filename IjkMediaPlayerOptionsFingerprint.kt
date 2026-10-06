package app.revanced.patches.bilibili.video.player.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * 定位写入 ijk 播放器选项的那个方法 (`IjkMediaPlayerItem#setItemOptions`)。
 *
 * 8.91.1 适配: 旧锚点是两个 option 字符串常量
 * `"enable-decoder-switch"` / `"enable-use-https-when-forbidden"`, 但新宿主里这两个字符串
 * 已经不共存于任何方法 (`enable-use-https-when-forbidden` 整条被删)。
 *
 * 改为按**指令引用**锚定: 目标方法必须同时
 *  - 读 `IjkMediaConfigParams->mEnableHwCodec:Z` (补丁要在这里挂钩强制硬解), 且
 *  - 调用 `IIjkMediaPlayerItem->setOptionBundle(...)` (补丁要在这儿打印选项)。
 *
 * 实测 8.91.1 中同时满足两条的方法**有且只有一个**:
 *   `Ltv/danmaku/ijk/media/player/IjkMediaPlayerItem;->setItemOptions()V` (private, 无参)
 * 与旧指纹的 `parameters = [] / returnType = "V"` 形状一致。
 */
object IjkMediaPlayerOptionsFingerprint : MethodFingerprint(
    parameters = listOf(),
    returnType = "V",
    customFingerprint = { methodDef, _ ->
        val instructions = methodDef.implementation?.instructions
        var hasHwCodecField = false
        var hasSetOptionBundle = false
        if (instructions != null) {
            for (inst in instructions) {
                val ref = (inst as? ReferenceInstruction)?.reference?.toString() ?: continue
                when {
                    ref == "Ltv/danmaku/ijk/media/player/IjkMediaConfigParams;->mEnableHwCodec:Z" ->
                        hasHwCodecField = true
                    ref.contains("setOptionBundle") ->
                        hasSetOptionBundle = true
                }
            }
        }
        hasHwCodecField && hasSetOptionBundle
    }
)
