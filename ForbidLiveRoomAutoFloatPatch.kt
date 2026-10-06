package app.revanced.patches.bilibili.misc.other.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.bilibili.misc.other.fingerprints.LiveRoomSetFloatWindowFingerprint
import app.revanced.patches.bilibili.utils.isAbstract
import app.revanced.patches.bilibili.utils.isNative
import app.revanced.util.exception
import app.revanced.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

@Patch(
    name = "Forbid live room auto float",
    description = "禁止直播间点小窗播放时自动开启“播放被中断时自动小窗播放”",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object ForbidLiveRoomAutoFloatPatch : BytecodePatch(setOf(LiveRoomSetFloatWindowFingerprint)) {

    private data class CallSite(val method: Method, val index: Int, val argRegister: Int)

    /**
     * 适配说明 (B站 8.91.1):
     *
     * 旧实现在**无参 `()V`** 方法里找调用点, 并匹配 `IF_EQZ + CONST_4` 紧贴
     * `INVOKE_INTERFACE setFloatWindow(Z)V` 的指令序列 —— 也就是旧版是把常量直接传进去。
     *
     * 新宿主里这个调用点搬进了两参数的 `Lsf1/c$d;->sendEvent(String, Object[])V`, 而且参数是
     * 从事件负载算出来的:
     *     aget-object / check-cast Boolean / booleanValue() / move-result v7 / invoke-interface w0(v7)
     * 根本没有 CONST_4, 于是老模式永远匹配不上, 补丁抛 "not found startMiniFloatPlay method"。
     *
     * 现在改成: 只要类方法里出现对 `<interface>->w0(Z)V` 的 invoke-interface 就认。
     * 注入逻辑仍保持"设置为开时整个 setter 调用被跳过"的语义, 且**不占用额外寄存器**
     * (patcher 的 registerCount 只读, 没法加临时寄存器), 做法:
     *
     *     if-eqz   vARG, :call      # 原值 false → 原样调用
     *     invoke-static {}, LiveRoomPatch->disableAutoFloat()Z
     *     move-result vARG
     *     if-eqz   vARG, :restore   # 设置为关 → 还原原值
     *     goto     :skip            # 设置为开 → 跳过调用
     *     :restore
     *     const/4  vARG, 0x1        # 原值必为 true 才会走到这里, 直接还原成 1
     *     :call
     *     invoke-interface {vRECEIVER, vARG}, w0(Z)V
     *     :skip
     */
    override fun execute(context: BytecodeContext) {
        val (setClass, setMethod) = LiveRoomSetFloatWindowFingerprint.result?.let {
            it.classDef to it.method
        } ?: throw LiveRoomSetFloatWindowFingerprint.exception
        val interfaceType = setClass.interfaces.firstOrNull()
            ?: throw PatchException("fingerprint class has no interface")
        val iSetMethodSign = setMethod.toMutable().apply {
            definingClass = interfaceType
        }.toString()

        val callSites = mutableListOf<CallSite>()
        for (cls in context.classes) {
            for (m in cls.methods) {
                if (m.accessFlags.let { it.isAbstract() || it.isNative() }) continue
                val instructions = m.implementation?.instructions?.toList() ?: continue
                instructions.forEachIndexed { index, inst ->
                    if (inst.opcode != Opcode.INVOKE_INTERFACE) return@forEachIndexed
                    val i35c = inst as? Instruction35c ?: return@forEachIndexed
                    if (i35c.reference.toString() != iSetMethodSign) return@forEachIndexed
                    callSites += CallSite(m, index, i35c.registerD)
                }
            }
        }
        if (callSites.isEmpty()) throw PatchException("not found setFloatWindow call site")

        callSites.forEach { (method, index, argRegister) ->
            context.findClass(method.definingClass)!!.mutableClass.findMutableMethodOf(method).run {
                addInstructionsWithLabels(
                    index, """
                    if-eqz v$argRegister, :call
                    invoke-static {}, Lapp/revanced/bilibili/patches/LiveRoomPatch;->disableAutoFloat()Z
                    move-result v$argRegister
                    if-eqz v$argRegister, :restore
                    goto :skip
                    :restore
                    const/4 v$argRegister, 0x1
                    :call
                    nop
                """.trimIndent(),
                    ExternalLabel("skip", getInstruction(index + 1))
                )
            }
        }
    }
}
