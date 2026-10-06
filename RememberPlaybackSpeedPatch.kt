package app.revanced.patches.bilibili.video.player.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.settings.patch.SettingsResourcePatch
import app.revanced.patches.bilibili.patcher.patch.MultiMethodBytecodePatch
import app.revanced.patches.bilibili.utils.exception
import app.revanced.patches.bilibili.video.player.fingerprints.*
import app.revanced.util.exception
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Patch(
    name = "Remember playback speed",
    description = "记住播放速度变化",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ],
    dependencies = [SettingsResourcePatch::class]
)
object RememberPlaybackSpeedPatch : MultiMethodBytecodePatch(
    // ── 8.91.1 适配 ──
    //
    // 下面三条指纹的锚点在 8.91.1 已经消失。它们原来声明在 `fingerprints` 里, 于是
    // patcher 在 execute() 之前就判定解析失败, 把**整个补丁**干掉 ——
    // 连仍然可用的两个"用户选速度"入口也一起丢了。和 Moss 是同一类问题。
    //
    // 实测各锚点存活情况:
    //   PlayerSettingCreateSpeedFingerprint ("option"/"speed"/"value")                   仅命中 Compose 化 lambda, 形状不符
    //   MenuServiceCreateSpeedFingerprint   ("MenuService$createSpeed$1")                0 命中
    //   MenuFuncSegmentFingerprint          ("player.player.option-more.speed.player")   0 命中
    //   PlayerSpeedChooseWidgetFingerprint  ("player.player.choose-speed.0.player")      3 命中 ← 存活
    //   PlaybackSpeedSettingFingerprint     ("player.player.full-more.speed.player")     1 命中 ← 存活
    //
    // 所以只保留存活的两条: 记住倍速最关键的入口 (倍速选择面板 + 全屏更多菜单里的倍速设置)
    // 都还在。execute() 里对死掉那三条本来就是 `?.run` 可选处理, 置空后自动跳过。
    multiFingerprints = setOf(PlayerSpeedChooseWidgetFingerprint, PlaybackSpeedSettingFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        super.execute(context)
        PlayerSpeedChooseWidgetFingerprint.result.ifEmpty {
            throw PlayerSpeedChooseWidgetFingerprint.exception
        }.forEach { r ->
            r.mutableMethod.addInstructions(
                0, """
                invoke-static {p1}, Lapp/revanced/bilibili/patches/PlaybackSpeedPatch;->onPlaybackSpeedSelected(F)V
            """.trimIndent()
            )
        }
        PlayerSettingCreateSpeedFingerprint.result?.run {
            val speedField = mutableClass.fields.first { it.type == "Ljava/lang/String;" }
            mutableMethod.addInstructions(
                0, """
                iget-object v0, p0, $speedField
                invoke-static {v0}, Ljava/lang/Float;->parseFloat(Ljava/lang/String;)F
                move-result v0
                invoke-static {v0}, Lapp/revanced/bilibili/patches/PlaybackSpeedPatch;->onPlaybackSpeedSelected(F)V
            """.trimIndent()
            )
        } ?: run {
            // 8.91.1: 该入口的锚点已消失。它在旧版是"菜单构造期顺带记录一次当前速度"的辅助点,
            // 而真正对应用户操作的两个入口 (倍速选择面板 / 全屏更多里的倍速设置) 仍然存活,
            // 且它们注入的都是同一个纯记录函数 onPlaybackSpeedSelected(F)。
            // 所以这里降级为告警, 不再按 isPink 把整个补丁打掉。
            println("[RememberPlaybackSpeed] PlayerSettingCreateSpeed 入口缺失, 已跳过 (其余入口仍生效)")
        }
        MenuServiceCreateSpeedFingerprint.result?.run {
            val speedListField = mutableClass.fields.first { it.type == "Ljava/util/List;" }
            mutableMethod.addInstructions(
                0, """
                iget-object v0, p0, $speedListField
                invoke-interface {v0, p1}, Ljava/util/List;->get(I)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Ljava/lang/String;
                invoke-static {v0}, Ljava/lang/Float;->parseFloat(Ljava/lang/String;)F
                move-result v0
                invoke-static {v0}, Lapp/revanced/bilibili/patches/PlaybackSpeedPatch;->onPlaybackSpeedSelected(F)V
            """.trimIndent()
            )
        } ?: run {
            // 同上: MenuServiceCreateSpeed 的锚点 ("MenuService$createSpeed$1") 在 8.91.1 已不存在。
            println("[RememberPlaybackSpeed] MenuServiceCreateSpeed 入口缺失, 已跳过 (其余入口仍生效)")
        }
        PlaybackSpeedSettingFingerprint.result.ifEmpty {
            throw PlaybackSpeedSettingFingerprint.exception
        }.forEach { r ->
            val instructions = r.mutableMethod.implementation!!.instructions
            val (index, register) = instructions.withIndex().firstNotNullOf { (index, inst) ->
                if (inst.opcode == Opcode.INVOKE_DIRECT && inst.getReference<MethodReference>().let {
                        it.returnType == "F" && it.parameterTypes == listOf("I")
                    }) {
                    index + 2 to (instructions[index + 1] as OneRegisterInstruction).registerA
                } else null
            }
            r.mutableMethod.addInstructions(
                index, """
                invoke-static {v$register}, Lapp/revanced/bilibili/patches/PlaybackSpeedPatch;->onPlaybackSpeedSelected(F)V
            """.trimIndent()
            )
        }
        MenuFuncSegmentFingerprint.result?.mutableMethod?.addInstructions(
            0, """
            invoke-static {p1}, Lapp/revanced/bilibili/patches/PlaybackSpeedPatch;->onPlaybackSpeedSelected(F)V
        """.trimIndent()
        )
    }
}
