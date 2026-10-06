package app.revanced.patches.bilibili.misc.protobuf.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.removeInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.protobuf.fingerprints.MossMiddlewareGaiaFingerprint
import app.revanced.patches.bilibili.misc.protobuf.fingerprints.MossOkHttpInterceptorFingerprint
import app.revanced.patches.bilibili.misc.protobuf.fingerprints.MossServiceFingerprint
import app.revanced.patches.bilibili.utils.cloneMutable
import app.revanced.util.exception
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Patch(
    name = "Moss",
    description = "gRPC 通信引擎服务 hook",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object MossPatch : BytecodePatch(
    setOf(MossServiceFingerprint, MossMiddlewareGaiaFingerprint, MossOkHttpInterceptorFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        MossServiceFingerprint.result?.mutableClass?.methods?.let { methods ->
            methods.find { it.name == "blockingUnaryCall" }?.run {
                // ── 为什么不再"在方法末尾追加 after hook" ──
                //
                // 老写法把 `hookBlockingAfter` 追加到 `blockingUnaryCall` 的**末尾**, 前提是
                // 这个方法只有一个出口。B站 8.91.1 里它**中间就 return**:
                //   走 "ignet 降级名单" 分支时, 在 `KMossServiceImp->blockingUnaryCall` 之后直接
                //   `return-object`, 根本到不了末尾那段。
                // 实测后果: `hookBlockingBefore` 每次都执行, `hookBlockingAfter` **一次都不执行**
                //   → 所有 Moss 响应侧 hook 全部失效 (热门过滤/弹幕/播放地址/动态/搜索/评论…)。
                //
                // 现在改成 **wrapper**: 原方法整体改名保留, 新的 `blockingUnaryCall` 只负责
                //   before -> 调用原方法(try/catch) -> after -> 返回。
                // 这样无论原方法内部从哪条路径返回, after hook 都必然执行。
                val registerCount = implementation?.registerCount ?: 8
                val originName = "blockingUnaryCall_Origin"
                val definingType = definingClass
                val paramDescriptor = parameterTypes.joinToString("")
                val returnDescriptor = returnType

                val origin = cloneMutable(name = originName)
                methods.add(origin)

                val wrapper = cloneMutable(registerCount = registerCount, clearImplementation = true)
                methods.remove(this)
                wrapper.addInstructionsWithLabels(
                    0, """
                    invoke-static {p2}, Lapp/revanced/bilibili/patches/protobuf/MossPatch;->hookBlockingBefore(Lcom/google/protobuf/GeneratedMessageLite;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :proceed
                    sget-object v1, Lapp/revanced/bilibili/meta/HookFlags;->STOP_EXECUTION:Ljava/lang/Object;
                    if-eq v0, v1, :custom
                    const/4 v0, 0x0
                    return-object v0
                    :custom
                    check-cast v0, Lcom/google/protobuf/GeneratedMessageLite;
                    return-object v0
                    :proceed
                    const/4 v1, 0x0
                    const/4 v2, 0x0
                    invoke-virtual {p0, p1, p2, p3}, $definingType->$originName($paramDescriptor)$returnDescriptor
                    move-result-object v1
                    goto :after
                    move-exception v2
                    :after
                    invoke-static {p2, v1, v2}, Lapp/revanced/bilibili/patches/protobuf/MossPatch;->hookBlockingAfter(Lcom/google/protobuf/GeneratedMessageLite;Lcom/google/protobuf/GeneratedMessageLite;Lcom/bilibili/lib/moss/api/MossException;)Lcom/google/protobuf/GeneratedMessageLite;
                    move-result-object v0
                    return-object v0
                """.trimIndent()
                )
                methods.add(wrapper)

                // 异常表: 只罩住 `invoke-virtual`(原方法调用) 那一条, 处理入口是 `move-exception`
                val impl = wrapper.implementation
                if (impl != null) {
                    val invokeIndex = impl.instructions.indexOfFirst {
                        it.opcode == Opcode.INVOKE_VIRTUAL &&
                                it.getReference<MethodReference>()?.name == originName
                    }
                    val catchIndex = impl.instructions.indexOfFirst { it.opcode == Opcode.MOVE_EXCEPTION }
                    if (invokeIndex >= 0 && catchIndex >= 0) {
                        impl.addCatch(
                            "Lcom/bilibili/lib/moss/api/MossException;",
                            impl.newLabelForIndex(invokeIndex),
                            impl.newLabelForIndex(invokeIndex + 1),
                            impl.newLabelForIndex(catchIndex)
                        )
                    }
                }
            }
            val gainType = MossMiddlewareGaiaFingerprint.result?.classDef?.type
            methods.filter { it.name == "asyncUnaryCall" || it.name == "asyncServerStreamingCall" }.forEach {
                it.addInstructionsWithLabels(
                    0, """
                    ${
                        if (gainType != null) {
                            """
                                instance-of v0, p3, $gainType
                                if-nez v0, :jump
                            """.trimIndent()
                        } else ""
                    }
                    invoke-static {p2, p3}, Lapp/revanced/bilibili/patches/protobuf/MossPatch;->hookAsyncBefore(Lcom/google/protobuf/GeneratedMessageLite;Lcom/bilibili/lib/moss/api/MossResponseHandler;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :jump
                    sget-object v1, Lapp/revanced/bilibili/meta/HookFlags;->STOP_EXECUTION:Ljava/lang/Object;
                    if-eq v0, v1, :stop_execution
                    goto :modify_handler
                    :stop_execution
                    return-void
                    :modify_handler
                    check-cast v0, Lcom/bilibili/lib/moss/api/MossResponseHandler;
                    move-object p3, v0
                    :jump
                    nop
                """.trimIndent()
                )
            }
        } ?: throw MossServiceFingerprint.exception
        // ── 请求侧篡改 (区域限制的客户端伪装 / authorization / 网络类型固定) ──
        //
        // 旧实现挂在 Cronet 的 `org.chromium.net.impl.BidirectionalStreamBuilderImpl` 上。
        // **B站 8.91.1 已彻底移除 Cronet** (实测 `org/chromium/net/` 下 0 个类), 于是这里
        // `?: throw` 会把整个 MossPatch 连带干掉 —— 包括上面那段真正重要的
        // MossService 响应 hook (38 个 proto 处理器: 解锁番剧、弹幕、动态、热门、搜索、评论…),
        // 因为一个补丁里任何一步抛异常, 整个补丁的注入都不会生效。
        //
        // 现在把请求侧这一段降级为**可选**: 装不上只丢请求侧能力, 响应侧 38 个 hook 照常工作。
        //
        // 后续跟进(已实现): 新宿主的 Moss gRPC 走 OkHttp, 拦截器是
        //   `Lqw2/a;->intercept(Lokhttp3/Interceptor$Chain;)Lokhttp3/Response;`
        // 见下方 MossOkHttpInterceptorFingerprint 的注入。
        runCatching {
            val builderClass = context.findClass("Lorg/chromium/net/impl/BidirectionalStreamBuilderImpl;")
                ?: throw PatchException("BidirectionalStreamBuilderImpl not present (Cronet removed)")
            builderClass.mutableClass.run {
                val urlField = fields.first { it.name == "mUrl" }
                val requestHeadersField = fields.first { it.name == "mRequestHeaders" }
                methods.first {
                    it.returnType == "Lorg/chromium/net/ExperimentalBidirectionalStream;" && it.parameterTypes.isEmpty()
                }.addInstructions(
                    0,
                    """
                    iget-object v0, p0, $urlField
                    iget-object v1, p0, $requestHeadersField
                    invoke-static {v0, v1}, Lapp/revanced/bilibili/patches/protobuf/MossPatch;->hookBeforeRequest(Ljava/lang/String;Ljava/util/ArrayList;)Ljava/lang/String;
                    move-result-object v0
                    iput-object v0, p0, $urlField
                """.trimIndent()
                )
            }
        }.onFailure {
            println("[MossPatch] 请求侧(Cronet)hook 跳过: $it —— 响应侧 38 个 Moss hook 不受影响")
        }

        // ── 请求侧篡改 (新路径: OkHttp) ──
        //
        // 宿主 `Lqw2/a;->intercept` 负责给 Moss 请求补 `x-bili-*-bin` 系列元数据头,
        // 最后 `Request.newBuilder().headers(...).build()` 交给 `Chain.proceed`。
        // 我们在 proceed 之前把 Request 过一遍集成侧, 叠上区域限制伪装 / authorization /
        // 网络类型固定 —— 也就是 Cronet 时代那套逻辑的等价物。
        //
        // 注意寄存器: 注入点取 `proceed` 指令的**第一个参数寄存器**(registerD),
        // 它正是要紧接着传进去的 Request, 覆写它即可。
        //
        // 这里失败只告警不抛: 请求侧是增强项, 绝不能像 Cronet 时代那样把响应侧 38 个 hook 一起拖死。
        runCatching {
            val method = MossOkHttpInterceptorFingerprint.result?.mutableMethod
                ?: throw PatchException("MossOkHttpInterceptorFingerprint unresolved")
            val instructions = method.implementation?.instructions
                ?: throw PatchException("intercept has no implementation")
            val proceedSign = "Lokhttp3/Interceptor\$Chain;->proceed(Lokhttp3/Request;)Lokhttp3/Response;"
            val index = instructions.indexOfFirst { inst ->
                inst.opcode == Opcode.INVOKE_INTERFACE
                        && (inst as? Instruction35c)?.reference?.toString() == proceedSign
            }
            if (index < 0) throw PatchException("proceed call not found in intercept")
            val requestRegister = (instructions.elementAt(index) as Instruction35c).registerD
            method.addInstructions(
                index, """
                invoke-static {v$requestRegister}, Lapp/revanced/bilibili/patches/protobuf/MossPatch;->hookBeforeRequestOkHttp(Lokhttp3/Request;)Lokhttp3/Request;
                move-result-object v$requestRegister
            """.trimIndent()
            )
            println("[MossPatch] 请求侧(OkHttp)hook 已挂载于 intercept#proceed, register=v$requestRegister")
        }.onFailure {
            println("[MossPatch] 请求侧(OkHttp)hook 跳过: $it —— 响应侧 38 个 Moss hook 不受影响")
        }
    }
}
