package com.bilibili.app.comm.supermenu.core;

import android.content.Context;

import java.util.List;

/**
 * 宿主 com.bilibili.app.comm.supermenu.core.MenuImpl 的编译桩。
 *
 * 签名取自宿主 {@code CardClickProcessor$e;->onMenuList} 的 smali：
 * <pre>
 *   MenuImpl-&gt;&lt;init&gt;(Landroid/content/Context;)V
 *   MenuImpl-&gt;setMenuItems(Ljava/util/List;)Lcom/bilibili/app/comm/supermenu/core/IMenu;
 * </pre>
 */
public class MenuImpl implements IMenu {

    public MenuImpl(Context context) {
    }

    @Override
    public List<IMenuItem> getMenuItems() {
        return null;
    }

    @Override
    public IMenu setMenuItems(List<IMenuItem> items) {
        return this;
    }
}
