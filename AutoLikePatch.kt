package app.revanced.patches.bilibili.misc.other.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.other.fingerprints.SectionFingerprint
import app.revanced.util.exception
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

@Patch(
    name = "Auto like",
    description = "视频自动点赞补丁",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object AutoLikePatch : BytecodePatch(setOf(SectionFingerprint)) {
    /**
     * ── 这个补丁在 B站 8.91.1 上是**过时的冗余路径**, 失败是预期结果 ──
     *
     * 它挂的是**旧版**首页 section 卡片的 ViewHolder 点赞回调:
     * 靠字符串 `"ActionViewHolder"` 定位类, 再去找父类里 1 参数返回 V 的 like 方法。
     *
     * 8.91.1 上:
     *  - 没有任何类名含 `ActionViewHolder` / `SectionViewHolder` / `LikeViewHolder`;
     *  - 宿主里含 `ViewHolder` 的字符串常量共 439 条, 逐条核对**没有一条**是 `ActionViewHolder`;
     *  - 任何 `ViewHolder` 类里也没有 `like*` 方法。
     * 所以锚点已被彻底移除, 而这个补丁是单条指纹、没有存活部分可保。
     *
     * **但是"自动点赞"这个功能本身在 8.91.1 上是可用的**, 走的是另一条更新的通路:
     *
     *   Moss 响应 hook `ViewUnite`(已注册)
     *     └─ ViewUniteReplyHook.hook()
     *          ├─ AutoLikePatch.detail = Pair(aid, like)
     *          └─ if (bizType == BIZ_TYPE_UGC) AutoLikePatch.autoLikeUnite()
     *                                             └─ activity.findViewById("frame_like").callOnClick()
     *
     * 该通路不依赖 ViewHolder, 只需要 activity + 资源 id, 而 `id/frame_like`
     * 在 8.91.1 里存在(0x7f091662)。已在成品 dex 中核实: `autoLikeUnite()` 虽被 R8 内联,
     * 但其完整逻辑(Settings.AutoLike 判断 / detail 判空 / likedVideos 去重 /
     * like!=0 跳过 / 500ms 后点击 frame_like 的 lambda)原样存在于 `Lkofua/i3;->V` 里。
     *
     * 因此这里**不做任何猜测性的重新锚定** —— 猜"哪个类的父类有 1 参数 V 虚方法"
     * 正是会造出 VerifyError 的那类改法。让它在构建日志里以明确的理由失败即可。
     */
    override fun execute(context: BytecodeContext) {
        val clazz = SectionFingerprint.result?.mutableClass
            ?: throw PatchException(
                "Auto like(legacy ViewHolder path) 不适用于 B站 8.91.1: " +
                        "旧锚点 \"ActionViewHolder\" 已从宿主移除。功能本身由 Moss " +
                        "ViewUnite hook -> AutoLikePatch.autoLikeUnite() 提供, 无需本补丁。"
            )
        val likeMethod = context.classes.first { it.type == clazz.superclass }.virtualMethods.find { m ->
            m.parameterTypes.size == 1 && m.returnType == "V" && !AccessFlags.FINAL.isSet(m.accessFlags)
        } ?: throw PatchException("can not found like method")
        val realLikeMethod = clazz.methods.first { m ->
            m.name == likeMethod.name && m.parameterTypes == likeMethod.parameterTypes
        }
        val insertIndex = realLikeMethod.implementation!!.instructions
            .indexOfLast { it.opcode == Opcode.RETURN_VOID }
        realLikeMethod.addInstruction(
            insertIndex, """
            invoke-static {p0}, Lapp/revanced/bilibili/patches/AutoLikePatch;->autoLike(Ljava/lang/Object;)V
        """.trimIndent()
        )
    }
}
