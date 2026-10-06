package app.revanced.bilibili.patches.json;

import android.text.TextUtils;

import androidx.annotation.Keep;

import com.alibaba.fastjson.annotation.JSONField;
// 注意: 不要直接 import 宿主的 DmAdvert / Dm。
// 它们在较新的宿主版本里已被移除或换包名 (8.91.1 中 adcommon.basic.model -> adcommon.data.model),
// 硬引用会在执行到 instanceof 时抛 NoClassDefFoundError; 该异常逃出 JSON.parseObject 钩子后
// 会被协程判为致命错误直接杀进程。这里改为按类名反射, 宿主没有这个类时整段逻辑自动跳过。
import com.bilibili.app.authorspace.api.BiliSpace;
import com.bilibili.app.comm.list.widget.recommend.RecommendModeGuidanceConfig;
import com.bilibili.app.gemini.ugc.feature.share.ShareIconResult;
import com.bilibili.bililive.room.biz.reverse.bean.LiveRoomReserveInfo;
import com.bilibili.bililive.room.biz.shopping.beans.LiveGoodsCardInfo;
import com.bilibili.bililive.room.biz.shopping.beans.LiveShoppingGotoBuyInfo;
import com.bilibili.bililive.room.biz.shopping.beans.LiveShoppingInfo;
import com.bilibili.bililive.room.biz.shopping.beans.LiveShoppingRecommendCardGoodsDetail;
import com.bilibili.bililive.videoliveplayer.net.beans.attentioncard.LiveRoomRecommendCard;
import com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.BiliLiveRoomInfo;
import com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.LiveRoomDanmakuVoteCardInfo;
import com.bilibili.bililive.videoliveplayer.net.beans.gateway.userinfo.BiliLiveRoomUserInfo;
import com.bilibili.bililive.videoliveplayer.net.beans.gateway.userinfo.FunctionCard;
import com.bilibili.bililive.videoliveplayer.net.beans.giftpendant.LiveGiftPendantInfo;
import com.bilibili.lib.homepage.mine.MenuGroup;
import com.bilibili.okretro.GeneralResponse;
import com.bilibili.pegasus.api.model.ChannelTabV2;
import com.bilibili.pegasus.api.model.ChannelV2;
import com.bilibili.video.story.api.StoryFeedResponse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.revanced.bilibili.meta.BottomItem;
import app.revanced.bilibili.settings.Settings;
import app.revanced.bilibili.utils.Constants;
import app.revanced.bilibili.utils.KtUtils;
import app.revanced.bilibili.utils.Logger;
import app.revanced.bilibili.utils.Reflex;
import app.revanced.bilibili.utils.Utils;
import app.revanced.bilibili.utils.Versions;
import kotlin.Lazy;
import kotlin.LazyKt;
import tv.danmaku.bili.ui.main.event.model.EventEntranceModel;
import tv.danmaku.bili.ui.main2.api.AccountMine;
import tv.danmaku.bili.ui.main2.resource.MainResourceManager;
import tv.danmaku.bili.ui.offline.api.EpPlayable;
import tv.danmaku.bili.ui.offline.api.OgvApiResponse;
import tv.danmaku.bili.ui.offline.api.OgvApiResponseV2;
import tv.danmaku.bili.ui.splash.ad.model.SplashData;
import tv.danmaku.bili.ui.splash.ad.model.SplashShowData;
import tv.danmaku.bili.ui.splash.brand.model.BrandSplashData;
import tv.danmaku.bili.ui.splash.event.EventSplashData;
import tv.danmaku.bili.ui.splash.event.EventSplashDataList;

public class JSONPatch {
    public static List<BottomItem> drawerItems = new ArrayList<>();
    public static List<BottomItem> bottomItems = new ArrayList<>();
    private static final Lazy<String> tabIdFieldName = LazyKt.lazy(() -> {
        var name = "";
        for (Field field : ChannelTabV2.class.getDeclaredFields()) {
            JSONField jsonField = field.getAnnotation(JSONField.class);
            if (jsonField != null && "id".equals(jsonField.name())) {
                name = field.getName();
                break;
            }
        }
        return name;
    });

    /**
     * 宿主弹幕广告模型, 按类名反射获取。
     * 取不到时 [dmAdvertClass] 为 null, 相关分支静默跳过, 不会让整个 JSON 钩子崩掉。
     */
    private static final Lazy<Class<?>> dmAdvertClass = LazyKt.lazy(() -> {
        try {
            return Class.forName("com.bilibili.ad.adview.videodetail.danmakuv2.model.DmAdvert");
        } catch (Throwable t) {
            return null;
        }
    });

    private static final Lazy<java.lang.reflect.Method> dmAdvertGetAds = LazyKt.lazy(() -> {
        Class<?> clazz = dmAdvertClass.getValue();
        if (clazz == null) return null;
        try {
            return clazz.getMethod("getAds");
        } catch (Throwable t) {
            return null;
        }
    });

    private static boolean isDmAdvert(Object data) {
        Class<?> clazz = dmAdvertClass.getValue();
        return clazz != null && clazz.isInstance(data) && dmAdvertGetAds.getValue() != null;
    }

    @Keep
    public static Object parseObjectHook(Object obj) {
        try {
            return parseObjectHookInternal(obj);
        } catch (Throwable t) {
            Logger.error(t, () -> "JSONPatch, parse object hook error");
            throw t;
        }
    }

    private static Object parseObjectHookInternal(Object obj) {
        Object data = (obj instanceof GeneralResponse<?> resp) ? resp.data : obj;
        if (data instanceof SplashData splashData) {
            if (Settings.PurifySplash.get()) try {
                splashData.splashList.clear();
                splashData.strategyList.clear();
            } catch (Throwable ignored) {
                splashData.getSplashList().clear();
                splashData.getStrategyList().clear();
            }
        } else if (data instanceof SplashShowData showData) {
            if (Settings.PurifySplash.get()) try {
                showData.strategyList.clear();
            } catch (Throwable ignored) {
                showData.getStrategyList().clear();
            }
        } else if (data instanceof EventEntranceModel) {
            if (Settings.PurifyGame.get()) {
                // no problem, see com.bilibili.okretro.BiliApiDataCallback
                return null;
            }
        } else if (isDmAdvert(data)) {
            if (Settings.BlockUpRcmdAds.get()) try {
                Object ads = dmAdvertGetAds.getValue().invoke(data);
                if (ads instanceof java.util.Collection<?> collection)
                    collection.clear();
            } catch (Throwable ignored) {
            }
        } else if (data instanceof LiveShoppingInfo info) {
            if (Settings.PurifyLivePopups.get().contains("shoppingCard")) {
                info.shoppingCardDetail = null;
                info.recommendCardDetail = null;
            }
        } else if (data instanceof LiveGoodsCardInfo || data instanceof LiveShoppingRecommendCardGoodsDetail) {
            if (Settings.PurifyLivePopups.get().contains("shoppingCard"))
                return null;
        } else if (data instanceof BiliLiveRoomInfo roomInfo) {
            Set<? extends String> keys = Settings.PurifyLivePopups.get();
            BiliLiveRoomInfo.FunctionCard card = roomInfo.functionCard;
            if (keys.contains("follow")) {
                if (card != null)
                    card.followCard = null;
            }
            if (keys.contains("wish")) try {
                if (card != null)
                    card.wishlistCard = null;
            } catch (Throwable ignored) {
            }
            if (keys.contains("banner"))
                roomInfo.bannerInfo = null;
            if (keys.contains("giftStar")) try {
                LiveGiftPendantInfo pendantInfo = roomInfo.revenueGiftPendantInfo;
                if (pendantInfo != null)
                    pendantInfo.liveGiftStarPendantInfo = null;
            } catch (Throwable ignored) {
            }
            if (keys.contains("plusOne")) try {
                roomInfo.dmComboInfo = null;
            } catch (Throwable ignored) {
            }
            if (Settings.RemoveLiveMask.get()) try {
                roomInfo.areaMaskInfo = null;
            } catch (Throwable ignored) {
            }
            if (Settings.RemoveLiveWatermark.get()) {
                Map<String, Object> newSwitchInfo = roomInfo.newSwitchInfo;
                if (newSwitchInfo != null)
                    newSwitchInfo.put("room-player-watermark", 0);
            }
            if (Settings.LiveNoBlock.get()) try {
                roomInfo.blockInfo = null;
            } catch (Throwable ignored) {
            }
        } else if (data instanceof LiveRoomRecommendCard) {
            if (Settings.PurifyLivePopups.get().contains("follow"))
                return null;
        } else if (data instanceof LiveRoomReserveInfo info) {
            if (Settings.PurifyLivePopups.get().contains("reserve"))
                info.showReserveDetail = false;
        } else if (data instanceof BiliLiveRoomUserInfo info) {
            Set<? extends String> keys = Settings.PurifyLivePopups.get();
            if (keys.contains("gift")) {
                FunctionCard card = info.functionCard;
                if (card != null)
                    card.sengGiftCard = null;
            }
            if (keys.contains("task"))
                info.taskInfo = null;
            if (keys.contains("playTogether")) {
                info.playTogetherInfo = null;
                try {
                    info.playTogetherInfoV2 = null;
                } catch (Throwable ignored) {
                }
            }
            if (keys.contains("qoe")) try {
                info.qoe = null;
            } catch (Throwable ignored) {
            }
        } else if (data instanceof LiveShoppingGotoBuyInfo) {
            if (Settings.PurifyLivePopups.get().contains("gotoBuy"))
                return null;
        } else if (data instanceof AccountMine accountMine) {
            customizeMine(accountMine);
        } else if (data instanceof MainResourceManager.TabResponse tabResponse) {
            customizeHomeTab(tabResponse.tabData);
        } else if (data instanceof BiliSpace biliSpace) {
            // 兜底：UP 主空间的自定义是**可选装饰**，它出任何问题都不该让整个响应解析失败 ——
            // parseObjectHook 会把异常重新抛给 fastjson，结果就是 UP 主空间直接打不开。
            try {
                customizeSpace(biliSpace);
            } catch (Throwable t) {
                Logger.error(t, () -> "JSONPatch, customize space failed");
            }
        } else if (data instanceof OgvApiResponse<?> ogvApiResponse) {
            unlockOgvResponse(ogvApiResponse);
        } else if (!Utils.isHd() && data instanceof OgvApiResponseV2 ogvApiResponseV2) {
            unlockOgvResponseV2(ogvApiResponseV2);
        } else if (data instanceof StoryFeedResponse feedResponse) {
            PegasusPatch.filterStory(feedResponse);
        } else if (data instanceof EventSplashDataList splashList) {
            if (Settings.PurifySplash.get()) {
                List<EventSplashData> eventList = splashList.getEventList();
                if (eventList != null && !eventList.isEmpty())
                    eventList.removeIf(splash -> !splash.isBirthdayData());
            }
        } else if (data instanceof RecommendModeGuidanceConfig) {
            if (Settings.BlockRecommendGuidance.get())
                return null;
        } else if (data instanceof BrandSplashData brandSplashData) {
            if (Settings.PurifySplash.get()) {
                brandSplashData.setBrandList(null);
                brandSplashData.setPreloadList(null);
                brandSplashData.setQueryList(null);
                brandSplashData.setShowList(null);
            }
        } else if (data instanceof ChannelV2 channelV2) {
            if (Settings.AddChannel.get()) {
                var tabs = channelV2.tabs;
                // topic was deprecated by official
                if (tabs != null) try {
                    tabs.removeIf(tab -> "topic".equals(tab.tabId));
                } catch (Throwable t) {
                    // tabId field is obfuscated on hd
                    var tabIdFieldName = JSONPatch.tabIdFieldName.getValue();
                    if (!tabIdFieldName.isEmpty()) {
                        tabs.removeIf(tab -> "topic".equals(Reflex.getObjectField(tab, tabIdFieldName)));
                    } else {
                        Logger.error(t, () -> "topic channel tab remove failed");
                    }
                }
            }
        } else if ((!Utils.isHd() && data instanceof BiliLiveRoomInfo.DmComboInfo) || data instanceof LiveRoomDanmakuVoteCardInfo) {
            if (Settings.PurifyLivePopups.get().contains("plusOne"))
                return null;
        } else if ((Utils.isHd() && data instanceof tv.danmaku.bili.videopage.player.features.share.ShareIconResult) || (!Utils.isHd() && data instanceof ShareIconResult)) {
            // block share guide for old UGC player
            return null;
        }
        return obj;
    }

    @Keep
    public static void parseArrayHook(Class<?> type, ArrayList<?> list) {
    }

    public static boolean shouldShowing(Set<? extends String> items, String item) {
        if (items.contains(item)) return true;
        return items.size() == 1 && items.contains(Constants.ALL_VALUE);
    }

    private static void customizeMine(AccountMine mine) {
        drawerItems.clear();
        Set<? extends String> items = Settings.ShowingDrawerItems.get();
        boolean purifyRedDot = Settings.PurifyDrawerRedDot.get();
        int drawerStyle = Integer.parseInt(Settings.DrawerStyle.get());
        if (Utils.isHd()) {
            var menuGroups = Arrays.asList(mine.padSectionList, mine.recommendSectionList, mine.moreSectionList);
            for (int i = 0; i < menuGroups.size(); i++) {
                var menuItems = menuGroups.get(i);
                if (menuItems == null || menuItems.isEmpty()) continue;
                menuItems.removeIf(item -> {
                    String itemTitle = item.title;
                    if (TextUtils.isEmpty(itemTitle))
                        return false;
                    String uri = item.uri;
                    String id = String.valueOf(item.id);
                    boolean showing = shouldShowing(items, id);
                    if (!"设置".equals(itemTitle))
                        drawerItems.add(new BottomItem(itemTitle, uri, id));
                    if (purifyRedDot) {
                        item.redDot = 0;
                        item.redDotRorNew = false;
                    }
                    return !"设置".equals(itemTitle) && !showing;
                });
            }
            if (Settings.AddChannel.get()) {
                var channelItem = new MenuGroup.Item();
                channelItem.id = 114514;
                channelItem.title = "频道中心";
                channelItem.uri = "bilibili://pegasus/channel/discover";
                channelItem.icon = "http://i0.hdslb.com/bfs/archive/f6739d905dee57d2c0429d9b66acb3f39b294aff.png";
                mine.moreSectionList.add(0, channelItem);
            }
        } else {
            List<MenuGroup> sectionListV2 = mine.sectionListV2;
            if (sectionListV2 == null || sectionListV2.isEmpty())
                return;
            for (int i = 0; i < sectionListV2.size(); i++) {
                MenuGroup section = sectionListV2.get(i);
                String title = section.title;
                if (!TextUtils.isEmpty(title))
                    drawerItems.add(new BottomItem("【标题项目】", null, title));
                List<MenuGroup.Item> itemList = section.itemList;
                if (itemList == null || itemList.isEmpty())
                    continue;
                itemList.removeIf(item -> {
                    String itemTitle = item.title;
                    if (TextUtils.isEmpty(itemTitle))
                        return false;
                    String uri = item.uri;
                    String id = String.valueOf(item.id);
                    boolean showing = shouldShowing(items, id);
                    if (!"设置".equals(itemTitle))
                        drawerItems.add(new BottomItem(itemTitle, uri, id));
                    if (purifyRedDot) {
                        item.redDot = 0;
                        item.redDotRorNew = false;
                    }
                    return !"设置".equals(itemTitle) && !showing;
                });
                MenuGroup.MineButton button = section.button;
                if (button != null) {
                    String buttonText = button.text;
                    if (!TextUtils.isEmpty(buttonText)) {
                        String uri = button.jumpUrl;
                        boolean showing = shouldShowing(items, buttonText);
                        drawerItems.add(new BottomItem("按钮：", uri, buttonText));
                        if (!showing)
                            section.button = null;
                    }
                }
                if (drawerStyle != 0)
                    section.style = drawerStyle;

                if (Utils.isPlay() && Settings.AddPodcast.get() && "更多服务".equals(title)
                        && itemList.stream().noneMatch(item -> "bilibili://podcast".equals(item.uri))) {
                    var podcastItem = new MenuGroup.Item();
                    podcastItem.id = 811;
                    podcastItem.title = "听视频";
                    podcastItem.uri = "bilibili://podcast";
                    podcastItem.icon = "http://i0.hdslb.com/bfs/feed-admin/97276c5df099e516946682edf4ef10dc6b18c7dc.png";
                    itemList.add(0, podcastItem);
                }
                if ("更多服务".equals(title) && Settings.AddChannel.get()) {
                    var channelItem = new MenuGroup.Item();
                    channelItem.id = 114514;
                    channelItem.title = "频道中心";
                    channelItem.uri = "bilibili://pegasus/channel/discover";
                    channelItem.icon = "http://i0.hdslb.com/bfs/archive/f6739d905dee57d2c0429d9b66acb3f39b294aff.png";
                    itemList.add(0, channelItem);
                }
                // for pink client to fix setting icon jitter when customizing dpi
                if ("更多服务".equals(title) && itemList.stream().noneMatch(item -> "activity://main/preference".equals(item.uri))) {
                    var settingItem = new MenuGroup.Item();
                    settingItem.id = 458;
                    settingItem.title = "设置";
                    settingItem.uri = "activity://main/preference";
                    settingItem.icon = "http://i0.hdslb.com/bfs/archive/e932404f2ee62e075a772920019e9fbdb4b5656a.png";
                    itemList.add(settingItem);
                }
            }
            sectionListV2.removeIf(section -> !TextUtils.isEmpty(section.title) && !shouldShowing(items, section.title));
        }
        if (Settings.CustomTheme.get()) {
            if (Versions.ge7_48_0()) {
                var garbEntrance = new AccountMine.GarbEntrance();
                garbEntrance.uri = "activity://navigation/theme/";
                mine.garbEntrance = garbEntrance;
            } else {
                mine.garbEntrance = null;
            }
        }
        if (Settings.BlockTips.get()) {
            mine.liveTip = null;
            mine.gameTips = null;
        }
    }

    private static void customizeHomeTab(MainResourceManager.TabData data) {
        bottomItems.clear();
        Set<? extends String> items = Settings.ShowingBottomItems.get();
        if (!Utils.isHd()) data.bottom.removeIf(tab -> {
            var uri = tab.uri;
            var id = tab.tabId;
            var showing = shouldShowing(items, id);
            bottomItems.add(new BottomItem(tab.name, uri, id));
            return !showing;
        });

        if (!Utils.isHd() && Settings.Drawer.get()) {
            data.bottom.removeIf(tab -> {
                String uri = tab.uri;
                return !TextUtils.isEmpty(uri) && uri.startsWith("bilibili://user_center/mine");
            });
        }

        if (Settings.PurifyGame.get() && data.top != null) {
            data.top.removeIf(tab -> {
                String uri = tab.uri;
                return !TextUtils.isEmpty(uri) && uri.startsWith("bilibili://game_center/home");
            });
        }

        if (Settings.DisableHomeStory.get()) try {
            var topLeftInfo = data.topLeftInfo;
            if (topLeftInfo != null && topLeftInfo.url != null && topLeftInfo.url.startsWith("bilibili://videoshortcut"))
                data.topLeftInfo = null;
        } catch (Throwable ignored) {
        }

        configTab(data);
    }

    private static void configTab(MainResourceManager.TabData data) {
        var tabs = data.tab;
        if (Utils.isHd() && (tabs == null || tabs.isEmpty())) {
            tabs = new ArrayList<>();
            data.tab = tabs;
            var liveTab = newTab("20", "直播", "bilibili://live/home", "直播tab", 1);
            var feedTab = newTab("24", "推荐", "bilibili://pegasus/promo", "推荐tab", 2, true);
            var hotTab = newTab("27", "热门", "bilibili://pegasus/hottopic", "hottopic", 3);
            var animeTab = newTab("30", "追番", "bilibili://pgc/home", "bangumi", 4);
            var cinemaTab = newTab("13", "影视", "bilibili://pgc/home?home_flow_type=2", "film", 5);
            tabs.addAll(Arrays.asList(liveTab, feedTab, hotTab, animeTab, cinemaTab));
        }
        if (tabs == null || tabs.isEmpty())
            return;
        var hasBangumiCN = false;
        var hasBangumiTW = false;
        var hasMovieCN = false;
        var hasMovieTW = false;
        var hasKoreaHK = false;
        var hasKoreaTW = false;
        for (int i = 0; i < tabs.size(); i++) {
            var tab = tabs.get(i);
            switch (tab.uri) {
                case "bilibili://pgc/home" -> hasBangumiCN = true;
                case "bilibili://following/home_activity_tab/6544" -> hasBangumiTW = true;
                case "bilibili://pgc/home?home_flow_type=2",
                     "bilibili://pgc/cinema-tab", "bilibili://pgc/cinema_v2" -> hasMovieCN = true;
                case "bilibili://following/home_activity_tab/168644" -> hasMovieTW = true;
                case "bilibili://following/home_activity_tab/163541" -> hasKoreaHK = true;
                case "bilibili://following/home_activity_tab/95636" -> hasKoreaTW = true;
            }
        }
        if (Settings.AddBangumi.get()) {
            if (!hasBangumiCN) {
                var name = KtUtils.isChinaEnv() ? "追番" : "追番（大陸）";
                var tab = newTab("50", name, "bilibili://pgc/home", "bangumi", 50);
                tabs.add(tab);
            }
            if (!hasBangumiTW) {
                var tab = newTab("60", "追番（港澳台）", "bilibili://following/home_activity_tab/6544", "bangumi", 60);
                tabs.add(tab);
            }
        }
        if (Settings.AddMovie.get()) {
            if (!hasMovieCN) {
                var name = KtUtils.isChinaEnv() ? "影视" : "影視（大陸）";
                var tab = newTab("70", name, "bilibili://pgc/home?home_flow_type=2", "film", 70);
                tabs.add(tab);
            }
            if (!hasMovieTW) {
                var tab = newTab("80", "戏剧（港澳台）", "bilibili://following/home_activity_tab/168644", "jptv", 80);
                tabs.add(tab);
            }
        }
        if (Settings.AddKorea.get()) {
            if (!hasKoreaHK) {
                var tab = newTab("803", "韩综（港澳）", "bilibili://following/home_activity_tab/163541", "koreavhk", 803);
                tabs.add(tab);
            }
            if (!hasKoreaTW) {
                var tab = newTab("804", "韩综（台湾）", "bilibili://following/home_activity_tab/95636", "koreavtw", 804);
                tabs.add(tab);
            }
        }
        Set<? extends String> tabSet = Settings.HidedHomeTab.get();
        if (tabSet.isEmpty()) return;
        tabs.removeIf(tab -> switch (tab.uri) {
            case "bilibili://live/home" -> tabSet.contains("live");
            case "bilibili://pegasus/promo" -> tabSet.contains("promo");
            case "bilibili://pegasus/hottopic" -> tabSet.contains("hottopic");
            case "bilibili://pgc/home", "bilibili://following/home_activity_tab/6544" ->
                    tabSet.contains("bangumi");
            case "bilibili://pgc/home?home_flow_type=2",
                 "bilibili://pgc/cinema-tab", "bilibili://pgc/cinema_v2",
                 "bilibili://following/home_activity_tab/168644" -> tabSet.contains("movie");
            case "bilibili://following/home_activity_tab/95636",
                 "bilibili://following/home_activity_tab/163541" -> tabSet.contains("korea");
            default -> tabSet.contains("other_tabs");
        });
    }

    private static MainResourceManager.Tab newTab(String tabId, String name, String uri, String reportId, int pos) {
        return newTab(tabId, name, uri, reportId, pos, false);
    }

    private static MainResourceManager.Tab newTab(String tabId, String name, String uri, String reportId, int pos, boolean selected) {
        var tab = new MainResourceManager.Tab();
        tab.tabId = tabId;
        tab.name = name;
        tab.uri = uri;
        tab.reportId = reportId;
        tab.pos = pos;
        tab.selected = selected ? 1 : 0;
        return tab;
    }

    /**
     * 按字段名写值，字段不存在就安静跳过。
     *
     * 不能直接写 `target.xxx = null`：B站 8.91.1 起不少字段被移除，硬引用会抛
     * {@link NoSuchFieldError}，而 {@link #parseObjectHook} 记完日志会把它**重新抛出**，
     * 整个响应解析随之失败 —— 真机表现就是"进不去 UP 主空间"。
     *
     * @param target    待修改的对象。
     * @param fieldName 字段名（可能是宿主已移除的）。
     * @param value     要写入的值。
     */
    private static void setFieldIfExists(Object target, String fieldName, Object value) {
        try {
            var field = Reflex.findFieldIfExists(target.getClass(), fieldName);
            if (field != null) field.set(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static void customizeSpace(BiliSpace space) {
        Set<? extends String> values = Settings.CustomizeSpace.get();
        if (values.isEmpty()) return;
        if (space.tab != null && !space.tab.isEmpty())
            space.tab.removeIf(tab -> {
                String param = tab.param;
                if (TextUtils.isEmpty(param))
                    return false;
                var subTabs = tab.items;
                if ("contribute".equals(param) && subTabs != null && !subTabs.isEmpty() && Settings.AddArticleTab.get()) {
                    var hasArticle = false;
                    var opusIndex = -1;
                    for (int i = 0; i < subTabs.size(); i++) {
                        var subParam = subTabs.get(i).param;
                        if ("article".equals(subParam)) {
                            hasArticle = true;
                        } else if ("opus".equals(subParam)) {
                            opusIndex = i;
                            break;
                        }
                    }
                    if (!hasArticle && opusIndex != -1) {
                        var articleTab = new BiliSpace.Tab();
                        articleTab.title = "专栏";
                        articleTab.param = "article";
                        subTabs.add(opusIndex, articleTab);
                    }
                }
                String tabKey = "tab." + param;
                return values.contains(tabKey);
            });
        for (String value : values) {
            switch (value) {
                // B站 8.91.1 起移除了不少字段。这里**不能**直接写 `space.xxx = null`：
                // 字段不存在时硬引用会抛 NoSuchFieldError，而 parseObjectHook 记完日志
                // 会把它**重新抛出**，整个响应解析就废了 —— 真机表现就是"进不去 UP 主空间"。
                // 统一改走"字段在就清、不在就跳过"的反射写入。
                case "liveEntry" -> setFieldIfExists(space, "liveEntry", null);
                case "chargeResult" -> setFieldIfExists(space, "chargeResult", null);
                case "guard" -> setFieldIfExists(space, "guard", null);
                case "adV2" -> {
                    setFieldIfExists(space, "ad", null);
                    setFieldIfExists(space, "adV2", null);
                }
                case "archiveVideo" -> setFieldIfExists(space, "archiveVideo", null);
                case "article" -> setFieldIfExists(space, "article", null);
                case "audio" -> setFieldIfExists(space, "audio", null);
                case "season" -> setFieldIfExists(space, "season", null);
                case "coinVideo" -> setFieldIfExists(space, "coinVideo", null);
                case "recommendVideo" -> setFieldIfExists(space, "recommendVideo", null);
                case "followComicList" -> setFieldIfExists(space, "followComicList", null);
                case "spaceGame" -> setFieldIfExists(space, "spaceGame", null);
                case "cheeseVideo" -> setFieldIfExists(space, "cheeseVideo", null);
                case "fansDress" -> setFieldIfExists(space, "fansDress", null);
                case "favoriteBox" -> setFieldIfExists(space, "favoriteBox", null);
                case "comicList" -> setFieldIfExists(space, "comicList", null);
                case "ugcSeasonList" -> setFieldIfExists(space, "ugcSeasonList", null);
                case "contractResource" -> setFieldIfExists(space, "contractResource", null);
                case "nftShowModule" -> setFieldIfExists(space, "nftShowModule", null);
            }
        }
        try {
            if (space.buttonEntranceList != null) {
                space.buttonEntranceList.removeIf(entrance -> {
                    String moduleType = entrance.moduleType;
                    return values.contains("chargeResult") && "charge".equals(moduleType)
                            || values.contains("guard") && "navigation".equals(moduleType)
                            || values.contains("adV2") && "goods".equals(moduleType);
                });
            }
        } catch (NoSuchFieldError ignored) {
        }
    }

    private static void unlockOgvResponse(OgvApiResponse<?> response) {
        if (!Settings.AllowDownload.get()) return;
        if (!(response.result instanceof List<?> items)) return;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof EpPlayable epPlayable)
                epPlayable.isPlayable = 1;
        }
    }

    private static void unlockOgvResponseV2(OgvApiResponseV2 response) {
        if (!Settings.AllowDownload.get()) return;
        var data = response.getData();
        if (data == null) return;
        var params = data.getEpPlayableParams();
        if (params == null || params.isEmpty()) return;
        for (int i = 0; i < params.size(); i++)
            params.get(i).setPlayableType(0);
    }
}
