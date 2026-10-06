package app.revanced.patches.bilibili.misc.other.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.other.fingerprints.QualityViewHolderFingerprint
import app.revanced.patches.bilibili.patcher.patch.MultiMethodBytecodePatch
import app.revanced.patches.bilibili.utils.cloneMutable
import app.revanced.patches.bilibili.utils.exception
import com.android.tools.smali.dexlib2.iface.Method

@Patch(
    name = "Trial quality",
    description = "试用画质辅助补丁",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object TrialQualityPatch : MultiMethodBytecodePatch(
    multiFingerprints = setOf(QualityViewHolderFingerprint)
) {
    /**
     * 适配说明 (B站 8.91.1):
     *
     * 旧实现用 `associate { ... .first { 5 参数谓词 } }`。新宿主上 `QualityViewHolderFingerprint`
     * 多匹配到一个 `playerbizcommonv2.widget.quality.f0`, 它的对应绑定方法 `q0(...)` 是 **6 个参数**
     * (末尾多了 `QualityItemStyle`), 于是 `first {}` 直接抛
     * `NoSuchElementException: Collection contains no element matching the predicate`,
     * 把本来完全匹配的 `com.mall.videodetail.vd.united.page.videoquality.b0#q0` 一起带崩。
     *
     * 现在:
     *  - 用 `mapNotNull` 只挑出"确实有绑定方法"的类, 没有的跳过, 不再整体失败;
     *  - 参数谓词接受 5 或 6 个参数 (两者的前 5 个位置含义一致:
     *    `[1]=Z [3]=TextView [4]=TextView`, 也就是 `p2/p4/p5`);
     *  - 转发原方法时的寄存器范围按参数个数动态计算, 6 参数时用 `p0..p6`。
     */
    override fun execute(context: BytecodeContext) {
        super.execute(context)
        val patchMethod = context.findClass("Lapp/revanced/bilibili/patches/TrialQualityPatch;")!!
            .mutableClass.methods.first { it.name == "onBindOnline" }
        QualityViewHolderFingerprint.result.mapNotNull { r ->
            val method = r.mutableClass.methods.firstOrNull { it.isQualityBindMethod() }
                ?: return@mapNotNull null
            r.mutableClass.methods to method
        }.ifEmpty {
            throw QualityViewHolderFingerprint.exception
        }.forEach { (methods, method) ->
            val originMethod = method.cloneMutable(name = method.name + "_Origin")
                .also { methods.add(it) }
            // p0 = this, pN = 最后一个参数
            val lastRegister = method.parameterTypes.size
            method.also { methods.remove(it) }
                .cloneMutable(registerCount = lastRegister + 1, clearImplementation = true).apply {
                    addInstructions(
                        """
                        invoke-direct/range {p0 .. p$lastRegister}, $originMethod
                        invoke-static {p2, p4, p5}, $patchMethod
                        return-void
                    """.trimIndent()
                    )
                }.also { methods.add(it) }
        }
    }

    private fun Method.isQualityBindMethod(): Boolean = parameterTypes.let {
        (it.size == 5 || it.size == 6)
                && it[1] == "Z"
                && it[3] == "Landroid/widget/TextView;"
                && it[4] == "Landroid/widget/TextView;"
    }
}
