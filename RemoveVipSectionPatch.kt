package app.revanced.bilibili.patches

import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import app.revanced.bilibili.account.Accounts
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.*
import tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment

object RemoveVipSectionPatch {

    @JvmStatic
    private var currentResolvedThemeId = -1L

    @JvmStatic
    private var lastResolvedThemeId = -1L

    @JvmStatic
    private fun processingLayout(view: View?) {
        view ?: return
        // ── 8.91.1 适配 ──
        //
        // 原来的写法直接假定两个 view 一定存在, 结果在真机上点"我的"标签 **100% 崩溃**:
        //
        //   NullPointerException: Attempt to invoke virtual method
        //     'int android.view.ViewGroup.getChildCount()' on a null object reference
        //       at RemoveVipSectionPatch.processingLayout
        //       at RemoveVipSectionPatch.onUserCenterFragmentSkinChanged
        //       at HomeUserCenterFragment.onSkinChange
        //
        // 两个原因叠在一起:
        //  1. `onSkinChange` 会在 fragment view 尚未 inflate 完成时就被调用, 此时控件还找不到;
        //  2. B站把会员购区块的 id **改名**了 —— `mine_vip_layout` 在 8.91.1 的资源表里已不存在,
        //     取而代之的是 `mine_vip_layout_refactoring`。
        //
        // 所以: 先判空 (view 没就绪就直接返回, 等下一次回调), 再对新旧两个 id 做回退。
        val vipLayout = view.findView<ViewGroup>("mine_vip_layout")
            ?: view.findView<ViewGroup>("mine_vip_layout_refactoring")
            ?: return
        val mineRecycle = view.findView<ViewGroup>("mine_recycle") ?: return
        mineRecycle.clipToPadding = false
        if (Settings.RemoveVipSection()) {
            vipLayout.children.forEach { it.hide() }
            val currentThemeId = currentThemeId()
            val lastThemeId = lastThemeId()
            val pureThemeIds = CustomThemePatch.getThemeNames().values.map { it.toLong() }
            if (currentThemeId == 8L/*white*/ || (currentThemeId == 1L/*black*/ && (lastThemeId == -1L || lastThemeId in pureThemeIds))) {
                mineRecycle.updatePadding(top = 0)
                vipLayout.updateLayoutParams {
                    height = 0
                }
            } else {
                mineRecycle.updatePadding(top = 10.dp)
                vipLayout.updateLayoutParams {
                    height = 10.dp
                }
            }
        } else {
            vipLayout.children.forEach { it.show() }
            mineRecycle.updatePadding(top = 0)
            vipLayout.updateLayoutParams {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    @JvmStatic
    private fun currentThemeId(): Long {
        if (!Accounts.isLogin)
            return 8
        if (currentResolvedThemeId != -1L)
            return currentResolvedThemeId
        val confFile = Themes.garbConf
        return (if (confFile.isFile) runCatchingOrNull {
            confFile.readText().toJSONObject().optLong("id", 8)
        } ?: 8 else 8).also { currentResolvedThemeId = it }
    }

    @JvmStatic
    private fun lastThemeId(): Long {
        if (!Accounts.isLogin)
            return -1
        if (lastResolvedThemeId != -1L)
            return lastResolvedThemeId
        val confFile = Themes.lastGarbConf
        return (if (confFile.isFile) runCatchingOrNull {
            confFile.readText().toJSONObject().optLong("id", -1)
        } ?: -1 else -1).also { lastResolvedThemeId = it }
    }

    @Keep
    @JvmStatic
    fun onUserCenterFragmentResume(fragment: HomeUserCenterFragment) {
        processingLayout(fragment.view)
    }

    @Keep
    @JvmStatic
    fun onUserCenterFragmentSkinChanged(fragment: HomeUserCenterFragment) {
        currentResolvedThemeId = -1
        lastResolvedThemeId = -1
        processingLayout(fragment.view)
    }
}
