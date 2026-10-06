package app.revanced.patches.bilibili.misc.json.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patches.bilibili.misc.json.fingerprints.CardClickProcessorFingerprint
import app.revanced.patches.bilibili.misc.json.fingerprints.CardClickProcessorNewFingerprint
import app.revanced.patches.bilibili.misc.json.fingerprints.PegasusParserFingerprint
import app.revanced.patches.bilibili.utils.annotation
import app.revanced.patches.bilibili.utils.cloneMutable
import app.revanced.patches.bilibili.utils.proxy
import app.revanced.patches.bilibili.utils.value
import app.revanced.util.exception
import com.android.tools.smali.dexlib2.iface.Field
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

@Patch(
    name = "Pegasus hook",
    description = "首页推荐流hook",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object PegasusPatch : BytecodePatch(
    setOf(
        PegasusParserFingerprint,
        CardClickProcessorFingerprint,
        CardClickProcessorNewFingerprint
    )
) {
    override fun execute(context: BytecodeContext) {
        PegasusParserFingerprint.result?.run {
            val method = mutableClass.methods.first {
                it.returnType == "Lcom/bilibili/okretro/GeneralResponse;"
            }
            method.cloneMutable(registerCount = 2, clearImplementation = true).apply {
                method.name += "_Origin"
                addInstructions(
                    """
                    invoke-virtual {p0, p1}, $method
                    move-result-object p1
                    invoke-static {p1}, Lapp/revanced/bilibili/patches/json/PegasusPatch;->pegasusHook(Lcom/bilibili/okretro/GeneralResponse;)V
                    return-object p1
                """.trimIndent()
                )
            }.also {
                mutableClass.methods.add(it)
            }
        } ?: throw PegasusParserFingerprint.exception
        // banners 注入是可选的增强项: 8.91.1 把首页模型从 fastjson 迁到了 gson
        // (注解由 @JSONField(name="banner_item") 变成 @SerializedName(value="banner_item")),
        // 找不到就跳过 —— 绝不能让这一段把整个 Pegasus hook (解析器 hook / 卡片点击 hook) 带崩。
        runCatching {
            val (stockBannersItemClass, bannerItemFiled) = context.classes.firstNotNullOfOrNull { c ->
                if (c.superclass == "Lcom/bilibili/pegasus/api/model/BasicIndexItem;") c.fields.find {
                    it.bannerItemAnnotationName() == "banner_item"
                }?.let { c to it } else null
            } ?: throw PatchException("not found banner item field")
            val myBannersItemClassName = "Lapp/revanced/bilibili/meta/pegasus/BannersItem;"
            val myBannersItemClass = context.findClass(myBannersItemClassName)!!
            stockBannersItemClass.proxy(context).setSuperClass(myBannersItemClassName)
            myBannersItemClass.mutableClass.methods.run {
                find { it.name == "getBanners" }?.also { remove(it) }
                    ?.cloneMutable(3, clearImplementation = true)
                    ?.apply {
                        addInstructions(
                            """
                            move-object v0, p0
                            check-cast v0, $stockBannersItemClass
                            iget-object v1, v0, $bannerItemFiled
                            return-object v1
                        """.trimIndent()
                        )
                    }?.also { add(it) }
            }
        }.onFailure {
            println("[Pegasus] banner_item 注入跳过: $it")
        }
        fun MutableMethod.hookOnFeedClick() = addInstructionsWithLabels(
            0, """
            invoke-static {p3}, Lapp/revanced/bilibili/patches/json/PegasusPatch;->onFeedClick(Lcom/bilibili/app/comm/list/common/data/DislikeReason;)Z
            move-result v0
            if-eqz v0, :jump
            return-void
            :jump
            nop
        """.trimIndent()
        )
        CardClickProcessorFingerprint.result?.mutableMethod?.hookOnFeedClick()
            ?: throw CardClickProcessorFingerprint.exception
        // only exist on 7.63.0 alpha version now
        CardClickProcessorNewFingerprint.result?.mutableMethod?.hookOnFeedClick()

        // ── 热门卡片的 ⋮ : 分享面板 → 屏蔽面板 ──
        //
        // 宿主事实(8.91.1, 取自 smali):
        //   热门 ⋮ 走 CardClickProcessor$e.onMenuList(List menus),
        //   面板项由 CardClickProcessor->X(activity, holder, ThreePointV4Item) 构建 ——
        //   **只读 ThreePointV4(分享 + 稍后再看), 完全不读 threePointV2**。
        //   所以首页那套「漫游屏蔽」注入在热门没有落点, 热门只能分享。
        //
        // 这里把 e.onMenuList 整体接管: 开关打开时清空 menus 换成屏蔽项;
        // 点击由 e.handleClick(IMenuItem) 回调, 按 id 查表交给 onFeedClick。
        // 开关关着时两个 hook 都直接放行, 行为与官方一致。
        runCatching {
            val handlerType = "Lcom/bilibili/pegasus/card/base/CardClickProcessor\$e;"
            val handlerClass = context.findClass(handlerType)
                ?: throw PatchException("CardClickProcessor\$e not found")
            val handlerMethods = handlerClass.mutableClass.methods
            val ctxPref = "Lcom/bilibili/pegasus/card/base/CardClickProcessor\$e;->b:Landroidx/fragment/app/FragmentActivity;"
            val tpV4Pref = "Lcom/bilibili/pegasus/card/base/CardClickProcessor\$e;->d:Lcom/bilibili/pegasus/api/modelv2/ThreePointV4Item;"

            handlerMethods.firstOrNull {
                it.name == "onMenuList" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == "Ljava/util/List;"
            }?.addInstructionsWithLabels(
                0, """
                iget-object v1, p0, $ctxPref
                iget-object v2, p0, $tpV4Pref
                invoke-static {p1, v1, v2}, Lapp/revanced/bilibili/patches/json/PegasusPatch;->onPopularMenuList(Ljava/util/List;Landroid/content/Context;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :continue
                return-void
                :continue
                nop
            """.trimIndent()
            ) ?: throw PatchException("CardClickProcessor\$e.onMenuList not found")

            // 点击也得我们接管: 宿主 v0 里 itemId=="dislike" 的分支在热门这条路上
            // a$c 是 null, 点了什么都不发生, 所以面板项由我们自己处理。
            handlerMethods.firstOrNull {
                it.name == "handleClick" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == "Lcom/bilibili/app/comm/supermenu/core/IMenuItem;"
            }?.addInstructionsWithLabels(
                0, """
                iget-object v1, p0, $tpV4Pref
                invoke-static {p1, v1}, Lapp/revanced/bilibili/patches/json/PegasusPatch;->onPopularMenuClick(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :continue
                const/4 v0, 0x1
                return v0
                :continue
                nop
            """.trimIndent()
            ) ?: throw PatchException("CardClickProcessor\$e.handleClick not found")
            println("[Pegasus] 热门 ⋮ 分享→屏蔽 hook 已注入")
        }.onFailure {
            // 刻意不致命: hook 失败时热门只是保持官方的分享面板, 不影响其它功能。
            println("[Pegasus] 热门 ⋮ 分享→屏蔽 hook 注入失败(功能不可用, 其余不受影响): $it")
        }
    }

    /**
     * 取字段上标记的 JSON 名, 兼容两种序列化框架:
     * - 旧宿主: fastjson `@JSONField(name = "...")`
     * - 8.91.1: gson `@SerializedName(value = "...")`
     */
    private fun Field.bannerItemAnnotationName(): String? =
        annotation("Lcom/alibaba/fastjson/annotation/JSONField;")
            ?.value<StringEncodedValue>("name")?.value
            ?: annotation("Lcom/google/gson/annotations/SerializedName;")
                ?.value<StringEncodedValue>("value")?.value
}
