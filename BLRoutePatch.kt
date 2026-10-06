package app.revanced.patches.bilibili.misc.other.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.other.fingerprints.RouteRequestFingerprint
import app.revanced.patches.bilibili.utils.cloneMutable
import app.revanced.patches.bilibili.utils.isPublic
import app.revanced.patches.bilibili.utils.isStatic
import app.revanced.util.exception

@Patch(
    name = "BL route intercept",
    description = "哔哩哔哩页面路由修改",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object BLRoutePatch : BytecodePatch(setOf(RouteRequestFingerprint)) {

    private const val INTERCEPT = """
        invoke-static {p1}, Lapp/revanced/bilibili/patches/BLRoutePatch;->intercept(Landroid/net/Uri;)Landroid/net/Uri;
        move-result-object p1
    """

    /**
     * 适配说明 (B站 8.91.1):
     *
     * 旧实现用两条指纹分别定位 `RouteRequest$Builder` 和 `RouteRequest`:
     *   - `BLRouteBuilderFingerprint` 锚点 `"Builder(targetUri="` —— **该字符串在新宿主已被删除**,
     *     指纹解析失败, 整个补丁抛异常;
     *   - `RouteRequestFingerprint` 锚点 `"RouteRequest(targetUri="` —— 仍然存在。
     *
     * 新宿主里 Builder 已并入 `com.bilibili.lib.blrouter.RouteRequest` 本身
     * (它的 `<init>(Landroid/net/Uri;)V` 就是原先 Builder 的入口), 两条指纹合流。
     * 因此这里只保留 `RouteRequestFingerprint`, 并给该类的所有"首参为 Uri"的构造函数插桩。
     */
    override fun execute(context: BytecodeContext) {
        val result = RouteRequestFingerprint.result ?: throw RouteRequestFingerprint.exception
        val requestClass = result.mutableClass

        val uriCtors = requestClass.methods.filter { m ->
            m.name == "<init>" && m.parameterTypes.isNotEmpty() && m.parameterTypes[0] == "Landroid/net/Uri;"
        }
        if (uriCtors.isEmpty()) throw RouteRequestFingerprint.exception
        uriCtors.forEach { it.addInstructions(0, INTERCEPT) }

        val routeToMethod = context.classes.asSequence().flatMap { it.methods }.firstOrNull { m ->
            m.accessFlags.let { it.isStatic() && it.isPublic() }
                    && m.parameterTypes == listOf(result.classDef.type, "Landroid/content/Context;")
        } ?: throw RouteRequestFingerprint.exception

        val utilsClass = context.findClass("Lapp/revanced/bilibili/utils/Utils;")!!.mutableClass
        val utilsRouteToMethod = utilsClass.methods.firstOrNull { it.name == "routeTo" }
            ?: throw RouteRequestFingerprint.exception
        utilsRouteToMethod.also { utilsClass.methods.remove(it) }
            .cloneMutable(registerCount = 3, clearImplementation = true).apply {
                addInstructions(
                    """
                    new-instance v0, ${result.classDef}
                    invoke-direct {v0, p0}, ${result.classDef}-><init>(Landroid/net/Uri;)V
                    invoke-static {v0, p1}, $routeToMethod
                    return-void
                """.trimIndent()
                )
            }.also { utilsClass.methods.add(it) }
    }
}
