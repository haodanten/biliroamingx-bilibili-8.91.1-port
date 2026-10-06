package com.bilibili.app.comm.supermenu.core;

import java.util.List;

/**
 * 宿主 com.bilibili.app.comm.supermenu.core.IMenu 的编译桩。
 *
 * 面板接口。{@code getMenuItems()} 的擦除签名是 {@code ()Ljava/util/List;}，
 * 与宿主 smali 一致。
 */
public interface IMenu {
    List<IMenuItem> getMenuItems();

    IMenu setMenuItems(List<IMenuItem> items);
}
