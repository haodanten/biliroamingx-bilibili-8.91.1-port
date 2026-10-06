package app.revanced.patches.bilibili.misc.other.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.other.fingerprints.CommentConfigFingerprint
import app.revanced.patches.bilibili.utils.classDescriptor
import app.revanced.patches.bilibili.utils.isAbstract
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Patch(
    name = "Force comment time navigable",
    description = "允许分P及交互视频评论时间点可点击",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object ForceCommentNavigablePatch : BytecodePatch(fingerprints = setOf(CommentConfigFingerprint)) {
    override fun execute(context: BytecodeContext) {
        val primaryCommentMainFragmentType =
            "com.bilibili.app.comm.comment2.comments.view.PrimaryCommentMainFragment".classDescriptor
        val patchType = "app.revanced.bilibili.patches.ForceCommentNavigablePatch".classDescriptor
        val baseFragmentType = "com.bilibili.lib.ui.BaseFragment".classDescriptor
        context.findClass(primaryCommentMainFragmentType)?.mutableClass?.methods?.firstOrNull {
            it.name == "onCreate" && it.parameterTypes == listOf("Landroid/os/Bundle;")
        }?.addInstructions(
            0, """
            invoke-static {p0}, $patchType->onPrimaryCommentMainFragmentCreate($baseFragmentType)V
        """.trimIndent()
        ) ?: throw PatchException("not found PrimaryCommentMainFragment")
        CommentConfigFingerprint.result?.run {
            val seekEnabledField = findSeekEnabledField() ?: return@run
            mutableClass.methods.firstOrNull { m ->
                m.parameterTypes.isEmpty() && m.returnType == "Z" && !m.accessFlags.isAbstract()
                        && m.getInstruction(0).let {
                    if (it.opcode == Opcode.IGET_BOOLEAN) it.getReference<FieldReference>() else null
                } == seekEnabledField
            }?.addInstructionsWithLabels(
                0, """
                invoke-static {}, $patchType->enabled()Z
                move-result v0
                if-eqz v0, :jump
                const/4 v0, 0x1
                return v0
                :jump
                nop
            """.trimIndent()
            )
        }
    }

    /**
     * 找出 CommentConfig 里 `seekEnabled` 对应的布尔字段。
     *
     * 旧实现的思路是"在 `seekEnabled=` 这条字符串常量**之后**找第一条 IGET_BOOLEAN"。
     * 但 B站 8.91.1 的 `toString()` 把所有字段读取统一提到了方法开头, 字符串拼接全部堆在后面,
     * 于是那条 IGET_BOOLEAN 根本不存在, 补丁直接抛 `firstNotNullOf` 的 NoSuchElementException。
     *
     * 新思路: `toString()` 里字段的拼接顺序与字段读取顺序是对齐的, 只要数出
     * "`seekEnabled=` 之前出现过几次 append(Z)", 就能定位到它是第几个布尔字段。
     * 目标方法里 `seekEnabled` 正好是第 7 个布尔字段 (`j:Z`)。
     */
    private fun app.revanced.patcher.fingerprint.MethodFingerprintResult.findSeekEnabledField(): FieldReference? {
        val instructions = mutableMethod.getInstructions()
        val stringIndex = scanResult.stringsScanResult?.matches?.lastOrNull()?.index ?: return null

        var appendBooleanCount = 0
        for (i in 0 until stringIndex) {
            val inst = instructions[i]
            if (inst.opcode != Opcode.INVOKE_VIRTUAL) continue
            val ref = inst.getReference<MethodReference>() ?: continue
            if (ref.name == "append" && ref.parameterTypes == listOf("Z")) appendBooleanCount++
        }

        val booleanFields = buildList {
            for (i in 0 until stringIndex) {
                val inst = instructions[i]
                if (inst.opcode == Opcode.IGET_BOOLEAN)
                    inst.getReference<FieldReference>()?.let { add(it) }
            }
        }

        return booleanFields.getOrNull(appendBooleanCount)
    }
}
