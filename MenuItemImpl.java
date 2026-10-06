package com.bilibili.app.comm.supermenu.core;

import android.content.Context;

/**
 * 宿主 com.bilibili.app.comm.supermenu.core.MenuItemImpl 的编译桩。
 *
 * 构造签名取自宿主 {@code CardClickProcessor;->X(...)} 的 smali：
 * <pre>
 *   MenuItemImpl-&gt;&lt;init&gt;(Landroid/content/Context;Ljava/lang/String;II)V
 *   MenuItemImpl-&gt;&lt;init&gt;(Landroid/content/Context;Ljava/lang/String;ILjava/lang/CharSequence;)V
 * </pre>
 *
 * 第 2 个 String 是 **itemId**（宿主 {@code IMenuItem.getItemId()} 返回的就是它），
 * 第 3 个 int 是图标资源，第 4 个是标题 —— int 版是**字符串资源 id**，CharSequence 版是文本。
 */
public class MenuItemImpl implements IMenuItem {

    public MenuItemImpl(Context context, String itemId, int iconRes, int titleRes) {
    }

    public MenuItemImpl(Context context, String itemId, int iconRes, CharSequence title) {
    }
}
