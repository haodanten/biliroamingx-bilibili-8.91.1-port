package app.revanced.bilibili.patches.json;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.bapis.bilibili.app.card.v1.Base;
import com.bapis.bilibili.app.card.v1.Card;
import com.bapis.bilibili.app.card.v1.SmallCoverV5;
import com.bapis.bilibili.app.card.v1.ThreePointV2;
import com.bilibili.app.comm.supermenu.core.IMenu;
import com.bilibili.app.comm.supermenu.core.IMenuItem;
import com.bilibili.app.comm.supermenu.core.MenuImpl;
import com.bilibili.app.comm.supermenu.core.MenuItemImpl;
import com.bapis.bilibili.app.distribution.setting.pegasus.PegasusDeviceWithoutFplocalConfig;
import com.bapis.bilibili.app.show.popular.v1.PopularReply;
import com.bapis.bilibili.app.view.v1.Relate;
import com.bapis.bilibili.app.view.v1.RelatesFeedReply;
import com.bapis.bilibili.app.view.v1.ViewReply;
import com.bapis.bilibili.app.viewunite.common.Module;
import com.bapis.bilibili.app.viewunite.common.RelateCard;
import com.bapis.bilibili.app.viewunite.common.RelateCardType;
import com.bapis.bilibili.app.viewunite.common.Relates;
import com.bapis.bilibili.playershared.BizType;
import com.bilibili.adcommon.data.model.FeedAdInfo;
import com.bilibili.app.comm.list.common.api.model.PlayerArgs;
import com.bilibili.app.comm.list.common.data.DislikeReason;
import com.bilibili.app.comm.list.common.data.ThreePointItem;
import com.bilibili.okretro.GeneralResponse;
import com.bilibili.pegasus.api.model.BasicIndexItem;
import com.bilibili.pegasus.api.modelv2.Args;
import com.bilibili.pegasus.api.modelv2.BannerItemV2;
import com.bilibili.pegasus.api.modelv2.BasePlayerItem;
import com.bilibili.pegasus.api.modelv2.Config;
import com.bilibili.pegasus.api.modelv2.LargeCoverSingleV7Item;
import com.bilibili.pegasus.api.modelv2.LargeCoverSingleV8Item;
import com.bilibili.pegasus.api.modelv2.LargeCoverSingleV9Item;
import com.bilibili.pegasus.api.modelv2.LargeCoverV1Item;
import com.bilibili.pegasus.api.modelv2.LargeCoverV7Item;
import com.bilibili.pegasus.api.modelv2.LargeCoverV8Item;
import com.bilibili.pegasus.api.modelv2.LargeCoverV9Item;
import com.bilibili.pegasus.api.modelv2.PegasusFeedResponse;
import com.bilibili.pegasus.api.modelv2.SmallCoverItem;
import com.bilibili.pegasus.api.modelv2.SmallCoverV10Item;
import com.bilibili.pegasus.api.modelv2.SmallCoverV2Item;
import com.bilibili.pegasus.api.modelv2.SmallCoverV9Item;
import com.bilibili.pegasus.api.modelv2.Tag;
import com.bilibili.video.story.StoryDetail;
import com.bilibili.video.story.api.StoryFeedResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import app.revanced.bilibili.meta.pegasus.BannersItem;
import app.revanced.bilibili.settings.Settings;
import app.revanced.bilibili.utils.ArrayUtils;
import app.revanced.bilibili.utils.Jsons;
import app.revanced.bilibili.utils.KtUtils;
import app.revanced.bilibili.utils.Logger;
import app.revanced.bilibili.utils.Reflex;
import app.revanced.bilibili.utils.Toasts;
import app.revanced.bilibili.utils.Utils;
import app.revanced.bilibili.utils.Versions;
import kotlin.Unit;

public class PegasusPatch {

    private static Set<String> cachedTitileSet = Collections.emptySet();
    private static List<Pattern> cachedTitleRegexes = Collections.emptyList();
    private static Set<String> cachedReasonSet = Collections.emptySet();
    private static List<Pattern> cachedReasonRegexes = Collections.emptyList();
    private static Set<String> cachedUpSet = Collections.emptySet();
    private static List<Pattern> cachedUpRegexes = Collections.emptyList();

    private static final Pattern playCountRegex = Pattern.compile(".*?([\\d.]+)([万亿])?观看.*");
    private static final Pattern durationRegex = Pattern.compile("^((\\d{1,2}):)?(\\d{1,2}):(\\d{1,2})$");

    private static final long REASON_ID_TITLE = 1145140L;
    private static final long REASON_ID_RCMD_REASON = 1145141L;
    private static final long REASON_ID_UP_ID = 1145142L;
    private static final long REASON_ID_UP_NAME = 1145143L;
    private static final long REASON_ID_CATEGORY_NAME = 1145144L;
    private static final long REASON_ID_CHANNEL_NAME = 1145145L;
    private static final long[] blockReasonIds = new long[]{
            REASON_ID_TITLE,
            REASON_ID_RCMD_REASON,
            REASON_ID_UP_ID,
            REASON_ID_UP_NAME,
            REASON_ID_CATEGORY_NAME,
            REASON_ID_CHANNEL_NAME
    };

    private static final Map<String, List<String>> filterTypesMap = Map.ofEntries(
            Map.entry("advertisement", List.of("ad", "cm", "special_s")),
            Map.entry("article", List.of("article")),
            Map.entry("bangumi", List.of("bangumi", "special", "pgc")),
            Map.entry("game", List.of("game")),
            Map.entry("picture", List.of("picture")),
            Map.entry("vertical", List.of("vertical", "story")),
            Map.entry("banner", List.of("banner")),
            Map.entry("live", List.of("live")),
            Map.entry("inline", List.of("inline")),
            Map.entry("notify", List.of("notify_tunnel")),
            Map.entry("course", List.of("ketang")),
            Map.entry("comic", List.of("comic")),
            Map.entry("cannot_play", List.of("cannot_play")),
            Map.entry("large_cover", List.of("large_cover")),
            Map.entry("middle_cover", List.of("middle_cover")),
            Map.entry("small_cover", List.of("small_cover"))
    );

    private static List<String> getFilterTypes() {
        var filterSet = Settings.FilterHomeRecommend.get();
        return filterSet.stream().flatMap(s -> {
            var types = filterTypesMap.get(s);
            return types != null ? types.stream() : Stream.empty();
        }).collect(Collectors.toList());
    }

    private static boolean isNum(String str) {
        return !TextUtils.isEmpty(str) && str.chars().allMatch(Character::isDigit);
    }

    private static long toPlayCount(String str) {
        try {
            if (isNum(str))
                return Long.parseLong(str);
            if (str.contains("万"))
                return (long) (Double.parseDouble(str.replace("万", "")) * 10_000);
            if (str.contains("亿"))
                return (long) (Double.parseDouble(str.replace("亿", "")) * 100_000_000);
        } catch (Throwable ignored) {
        }
        return -1L;
    }

    private static long toPlayCount2(String str) {
        var matcher = playCountRegex.matcher(str);
        if (matcher.matches()) {
            var value = Double.parseDouble(Objects.requireNonNull(matcher.group(1)));
            var unit = matcher.group(2);
            return (long) value * ("万".equals(unit) ? 10_000 : ("亿".equals(unit) ? 100_000_000 : 1));
        }
        return -1L;
    }

    private static int toDuration(String str) {
        var matcher = durationRegex.matcher(str);
        if (matcher.matches()) {
            String hour = matcher.group(2);
            String minute = matcher.group(3);
            String second = matcher.group(4);
            var h = hour != null ? Integer.parseInt(hour) : 0;
            var m = minute != null ? Integer.parseInt(minute) : 0;
            var s = second != null ? Integer.parseInt(second) : 0;
            return h * 60 * 60 + m * 60 + s;
        }
        return -1;
    }

    private static String getCoverLeftText(BasicIndexItem item) {
        try {
            return getCoverLeftText0(item);
        } catch (Throwable t) {
            // 宿主较新版本移除了部分卡片模型类, instanceof 会抛 NoClassDefFoundError。
            // 这里降级为"取不到封面左侧文案", 不影响其余过滤逻辑。
            return null;
        }
    }

    private static String getCoverLeftText0(BasicIndexItem item) {
        String coverLeftText = null;
        if (item instanceof SmallCoverItem coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof SmallCoverV2Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof SmallCoverV9Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof SmallCoverV10Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof LargeCoverV1Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof LargeCoverV7Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof LargeCoverV8Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof LargeCoverV9Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof LargeCoverSingleV7Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof LargeCoverSingleV8Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        } else if (item instanceof LargeCoverSingleV9Item coverItem) {
            coverLeftText = coverItem.coverLeftText1;
        }
        return coverLeftText;
    }

    private static boolean isLowPlayCountVideo(BasicIndexItem item, long playCountLimit) {
        if (playCountLimit == 0L) return false;
        var coverLeftText = getCoverLeftText(item);
        if (TextUtils.isEmpty(coverLeftText)) return false;
        var playCount = toPlayCount(coverLeftText);
        if (playCount == -1L) return false;
        return playCount < playCountLimit;
    }

    private static boolean isLowPlayCountVideo(JSONObject item, long playCountLimit) {
        if (playCountLimit == 0L) return false;
        var coverLeftText = item.optString("cover_left_text_1");
        if (TextUtils.isEmpty(coverLeftText)) return false;
        var playCount = toPlayCount(coverLeftText);
        if (playCount == -1L) return false;
        return playCount < playCountLimit;
    }

    private static boolean isDurationInvalidVideo(BasicIndexItem item, int shortDuration, int longDuration) {
        if (shortDuration == 0 && longDuration == 0)
            return false;
        PlayerArgs playerArgs = item.playerArgs;
        long duration = playerArgs != null ? playerArgs.fakeDuration : 0;
        if (longDuration != 0 && duration > longDuration) return true;
        return shortDuration != 0 && duration < shortDuration;
    }

    private static boolean isDurationInvalidVideo(JSONObject item, int shortDuration, int longDuration) {
        if (shortDuration == 0 && longDuration == 0)
            return false;
        JSONObject playerArgs = item.optJSONObject("player_args");
        long duration = playerArgs != null ? playerArgs.optLong("duration") : 0;
        if (longDuration != 0 && duration > longDuration) return true;
        return shortDuration != 0 && duration < shortDuration;
    }

    private static boolean isContainsBlockKwdVideo(
            BasicIndexItem item,
            boolean titleRegexMode,
            Set<? extends String> titleSet,
            List<Pattern> titleRegexes,
            boolean reasonRegexMode,
            Set<? extends String> reasonSet,
            List<Pattern> reasonRegexes,
            boolean upRegexMode,
            Set<? extends String> upSet,
            List<Pattern> upRegexes,
            long[] uidArray,
            Set<? extends String> categorySet,
            Set<? extends String> channelSet
    ) {
        if (!titleSet.isEmpty()) {
            String title = item.title;
            if (titleRegexMode && !TextUtils.isEmpty(title)) {
                for (int i = 0; i < titleRegexes.size(); i++)
                    if (titleRegexes.get(i).matcher(title).find())
                        return true;
            } else if (!TextUtils.isEmpty(title))
                for (String s : titleSet)
                    if (title.contains(s))
                        return true;
        }

        if (uidArray.length > 0) {
            Args args = item.args;
            long upId = args != null ? args.upId : 0L;
            if (upId != 0L && ArrayUtils.contains(uidArray, upId))
                return true;
        }

        if (!upSet.isEmpty()) {
            String upName = getUpName(item);
            if (upRegexMode && !TextUtils.isEmpty(upName)) {
                for (int i = 0; i < upRegexes.size(); i++)
                    if (upRegexes.get(i).matcher(upName).find())
                        return true;
            } else if (!TextUtils.isEmpty(upName))
                for (String s : upSet)
                    if (upName.contains(s))
                        return true;
        }

        if (!categorySet.isEmpty()) {
            Args args = item.args;
            String category = args != null ? args.rname : null;
            if (!TextUtils.isEmpty(category))
                for (String s : categorySet)
                    if (category.contains(s))
                        return true;
        }

        if (!channelSet.isEmpty()) {
            Args args = item.args;
            String channel = args != null ? args.tname : null;
            if (!TextUtils.isEmpty(channel))
                for (String s : channelSet)
                    if (channel.contains(s))
                        return true;
        }

        if (!reasonSet.isEmpty()) {
            String reason = getRcmdReason(item);
            if (reasonRegexMode && !TextUtils.isEmpty(reason)) {
                for (int i = 0; i < reasonRegexes.size(); i++)
                    if (reasonRegexes.get(i).matcher(reason).find())
                        return true;
            } else if (!TextUtils.isEmpty(reason)) {
                for (String s : reasonSet)
                    if (reason.contains(s))
                        return true;
            }
        }

        return false;
    }

    private static boolean isContainsBlockKwdVideo(
            JSONObject item,
            boolean titleRegexMode,
            Set<? extends String> titleSet,
            List<Pattern> titleRegexes,
            boolean reasonRegexMode,
            Set<? extends String> reasonSet,
            List<Pattern> reasonRegexes,
            boolean upRegexMode,
            Set<? extends String> upSet,
            List<Pattern> upRegexes,
            long[] uidArray,
            Set<? extends String> categorySet,
            Set<? extends String> channelSet
    ) {
        if (!titleSet.isEmpty()) {
            String title = item.optString("title");
            if (titleRegexMode && !TextUtils.isEmpty(title)) {
                for (int i = 0; i < titleRegexes.size(); i++)
                    if (titleRegexes.get(i).matcher(title).find())
                        return true;
            } else if (!TextUtils.isEmpty(title))
                for (String s : titleSet)
                    if (title.contains(s))
                        return true;
        }

        var args = item.optJSONObject("args");
        if (uidArray.length > 0) {
            long upId = args != null ? args.optLong("up_id") : 0L;
            if (upId != 0L && ArrayUtils.contains(uidArray, upId))
                return true;
        }

        if (!upSet.isEmpty()) {
            String upName = args != null ? args.optString("up_name") : null;
            if (upRegexMode && !TextUtils.isEmpty(upName)) {
                for (int i = 0; i < upRegexes.size(); i++)
                    if (upRegexes.get(i).matcher(upName).find())
                        return true;
            } else if (!TextUtils.isEmpty(upName))
                for (String s : upSet)
                    if (upName.contains(s))
                        return true;
        }

        if (!categorySet.isEmpty()) {
            String category = args != null ? args.optString("rname") : null;
            if (!TextUtils.isEmpty(category))
                for (String s : categorySet)
                    if (category.contains(s))
                        return true;
        }

        if (!channelSet.isEmpty()) {
            String channel = args != null ? args.optString("tname") : null;
            if (!TextUtils.isEmpty(channel))
                for (String s : channelSet)
                    if (channel.contains(s))
                        return true;
        }

        if (!reasonSet.isEmpty()) {
            String reason = item.optString("rcmd_reason");
            if (reasonRegexMode && !TextUtils.isEmpty(reason)) {
                for (int i = 0; i < reasonRegexes.size(); i++)
                    if (reasonRegexes.get(i).matcher(reason).find())
                        return true;
            } else if (!TextUtils.isEmpty(reason)) {
                for (String s : reasonSet)
                    if (reason.contains(s))
                        return true;
            }
        }

        return false;
    }

    private static boolean isPromoteRelate(Relate relate, boolean removeRelatePromote) {
        return removeRelatePromote && (relate.getFromSourceType()) == 2L || "cm".equals(relate.getGoto());
    }

    private static boolean isNotAvRelate(Relate relate, boolean removeRelateOnlyAv) {
        return removeRelateOnlyAv && !"av".equals(relate.getGoto());
    }

    private static boolean isLowPlayCountRelate(Relate relate, long playCountLimit) {
        if (playCountLimit == 0L) return false;
        var text = relate.getStatV2().getViewVt().getText();
        if (TextUtils.isEmpty(text)) return false;
        var playCount = toPlayCount(text);
        if (playCount == -1L) return false;
        return playCount < playCountLimit;
    }

    private static boolean isDurationInvalidRelate(Relate relate, int shortDuration, int longDuration) {
        if (shortDuration == 0 && longDuration == 0)
            return false;
        long duration = relate.getDuration();
        if (longDuration != 0 && duration > longDuration) return true;
        return shortDuration != 0 && duration < shortDuration;
    }

    private static boolean isContainsBlockKwdRelate(
            Relate item,
            boolean titleRegexMode,
            Set<? extends String> titleSet,
            List<Pattern> titleRegexes,
            boolean reasonRegexMode,
            Set<? extends String> reasonSet,
            List<Pattern> reasonRegexes,
            boolean upRegexMode,
            Set<? extends String> upSet,
            List<Pattern> upRegexes,
            long[] uidArray
    ) {
        if (!titleSet.isEmpty()) {
            String title = item.getTitle();
            if (titleRegexMode && !TextUtils.isEmpty(title)) {
                for (int i = 0; i < titleRegexes.size(); i++)
                    if (titleRegexes.get(i).matcher(title).find())
                        return true;
            } else if (!TextUtils.isEmpty(title))
                for (String s : titleSet)
                    if (title.contains(s))
                        return true;
        }

        if (uidArray.length > 0) {
            long upId = item.getAuthor().getMid();
            if (upId != 0L && ArrayUtils.contains(uidArray, upId))
                return true;
        }

        if (!upSet.isEmpty()) {
            String upName = item.getAuthor().getName();
            if (upRegexMode && !TextUtils.isEmpty(upName)) {
                for (int i = 0; i < upRegexes.size(); i++)
                    if (upRegexes.get(i).matcher(upName).find())
                        return true;
            } else if (!TextUtils.isEmpty(upName))
                for (String s : upSet)
                    if (upName.contains(s))
                        return true;
        }

        if (!reasonSet.isEmpty()) {
            String reason = item.getRcmdReasonStyle().getText();
            if (reasonRegexMode && !TextUtils.isEmpty(reason)) {
                for (int i = 0; i < reasonRegexes.size(); i++)
                    if (reasonRegexes.get(i).matcher(reason).find())
                        return true;
            } else if (!TextUtils.isEmpty(reason)) {
                for (String s : reasonSet)
                    if (reason.contains(s))
                        return true;
            }
        }

        return false;
    }

    private static boolean isPromoteRelateUnite(RelateCard relate, boolean removeRelatePromote) {
        RelateCardType cardType = relate.getRelateCardType();
        return removeRelatePromote && (cardType == RelateCardType.RESOURCE || cardType == RelateCardType.CM || cardType == RelateCardType.GAME || cardType == RelateCardType.LIVE);
    }

    private static boolean isNotAvRelateUnite(RelateCard relate, boolean removeRelateOnlyAv, BizType bizType) {
        RelateCardType cardType = relate.getRelateCardType();
        return removeRelateOnlyAv && (bizType == BizType.BIZ_TYPE_UGC && cardType != RelateCardType.AV);
    }

    private static boolean isWideAvCard(RelateCard relate) {
        RelateCardType cardType = relate.getRelateCardType();
        String aiGoToType = relate.getAiCard().getGotoType();
        return cardType == RelateCardType.AV || (cardType == RelateCardType.AI_RECOMMEND && "bangumi-av".equals(aiGoToType));
    }

    private static boolean isLowPlayCountRelateUnite(RelateCard relate, long playCountLimit) {
        if (playCountLimit == 0L) return false;
        if (!isWideAvCard(relate)) return false;
        RelateCardType cardType = relate.getRelateCardType();
        var text = cardType == RelateCardType.AV ? relate.getAv().getStat().getVt().getText()
                : relate.getAiCard().getStat().getVt().getText();
        if (TextUtils.isEmpty(text)) return false;
        var playCount = toPlayCount(text);
        if (playCount == -1L) return false;
        return playCount < playCountLimit;
    }

    private static boolean isDurationInvalidRelateUnite(RelateCard relate, int shortDuration, int longDuration) {
        if (shortDuration == 0 && longDuration == 0)
            return false;
        if (!isWideAvCard(relate)) return false;
        RelateCardType cardType = relate.getRelateCardType();
        long duration = cardType == RelateCardType.AV ? relate.getAv().getDuration()
                : relate.getAiCard().getDuration();
        if (longDuration != 0 && duration > longDuration) return true;
        return shortDuration != 0 && duration < shortDuration;
    }

    private static boolean isContainsBlockKwdRelateUnite(
            RelateCard item,
            boolean titleRegexMode,
            Set<? extends String> titleSet,
            List<Pattern> titleRegexes,
            boolean reasonRegexMode,
            Set<? extends String> reasonSet,
            List<Pattern> reasonRegexes,
            boolean upRegexMode,
            Set<? extends String> upSet,
            List<Pattern> upRegexes,
            long[] uidArray
    ) {
        if (!isWideAvCard(item)) return false;
        RelateCardType cardType = item.getRelateCardType();

        if (!titleSet.isEmpty()) {
            String title = item.getBasicInfo().getTitle();
            if (titleRegexMode && !TextUtils.isEmpty(title)) {
                for (int i = 0; i < titleRegexes.size(); i++)
                    if (titleRegexes.get(i).matcher(title).find())
                        return true;
            } else if (!TextUtils.isEmpty(title))
                for (String s : titleSet)
                    if (title.contains(s))
                        return true;
        }

        if (uidArray.length > 0) {
            long upId = item.getBasicInfo().getAuthor().getMid();
            if (upId != 0L && ArrayUtils.contains(uidArray, upId))
                return true;
        }

        if (!upSet.isEmpty()) {
            String upName = item.getBasicInfo().getAuthor().getTitle();
            if (upRegexMode && !TextUtils.isEmpty(upName)) {
                for (int i = 0; i < upRegexes.size(); i++)
                    if (upRegexes.get(i).matcher(upName).find())
                        return true;
            } else if (!TextUtils.isEmpty(upName))
                for (String s : upSet)
                    if (upName.contains(s))
                        return true;
        }

        if (!reasonSet.isEmpty() && cardType == RelateCardType.AV) {
            String reason = item.getAv().getRcmdReason().getText();
            if (reasonRegexMode && !TextUtils.isEmpty(reason)) {
                for (int i = 0; i < reasonRegexes.size(); i++)
                    if (reasonRegexes.get(i).matcher(reason).find())
                        return true;
            } else if (!TextUtils.isEmpty(reason)) {
                for (String s : reasonSet)
                    if (reason.contains(s))
                        return true;
            }
        }

        return false;
    }

    private static void removeAdBanner(BasicIndexItem item) {
        try {
            removeAdBanner0(item);
        } catch (Throwable t) {
            // BannerItemV2 在新宿主里已移除
        }
    }

    private static void removeAdBanner0(BasicIndexItem item) {
        if (!(item instanceof BannersItem bannersItem)) return;
        List<BannerItemV2> banners = bannersItem.getBanners();
        if (banners == null || banners.isEmpty()) return;
        banners.removeIf(banner -> "ad".equals(banner.type));
    }

    private static String getRcmdReason(BasicIndexItem item) {
        try {
            return getRcmdReason0(item);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String getRcmdReason0(BasicIndexItem item) {
        Tag reason = null;
        if (item instanceof SmallCoverItem coverItem) {
            reason = coverItem.rcmdReason;
        } else if (item instanceof SmallCoverV10Item coverItem) {
            reason = coverItem.rcmdReason;
        } else if (item instanceof SmallCoverV2Item coverItem) {
            try {
                if (coverItem.rcmdReasonV2 != null) {
                    reason = coverItem.rcmdReasonV2;
                } else {
                    reason = coverItem.rcmdReason;
                }
            } catch (NoSuchFieldError ignored) {
                reason = coverItem.rcmdReason;
            }
        } else if (item instanceof SmallCoverV9Item coverItem) {
            reason = coverItem.rcmdReason;
        }
        if (reason != null)
            return reason.text;
        return null;
    }

    private static String getUpName(BasicIndexItem item) {
        try {
            return getUpName0(item);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String getUpName0(BasicIndexItem item) {
        Args args = item.args;
        if (args != null && !"picture".equals(item.goTo))
            return args.upName;
        if (!"picture".equals(item.goTo))
            return null;
        String desc = null;
        if (item instanceof SmallCoverItem coverItem) {
            desc = coverItem.desc;
        } else if (item instanceof SmallCoverV2Item coverItem) {
            desc = coverItem.desc;
        } else if (item instanceof SmallCoverV9Item coverItem) {
            desc = coverItem.desc;
        } else if (item instanceof LargeCoverV1Item coverItem) {
            desc = coverItem.desc;
        } else if (item instanceof LargeCoverSingleV7Item coverItem) {
            desc = coverItem.desc;
        } else if (item instanceof LargeCoverSingleV8Item coverItem) {
            desc = coverItem.desc;
        } else if (item instanceof LargeCoverSingleV9Item coverItem) {
            desc = coverItem.desc;
        }
        return desc;
    }

    private static void appendReasons(BasicIndexItem item) {
        String title = item.title;
        String rcmdReason = getRcmdReason(item);
        Args args = item.args;
        long upId = args != null ? args.upId : 0L;
        String upName = getUpName(item);
        String categoryName = args != null ? args.rname : null;
        String channelName = args != null ? args.tname : null;
        List<DislikeReason> reasons = new ArrayList<>();
        if (!TextUtils.isEmpty(title)) {
            DislikeReason reason = new DislikeReason();
            reason.id = REASON_ID_TITLE;
            reason.name = "标题:" + title;
            reasons.add(reason);
        }
        if (!TextUtils.isEmpty(rcmdReason)) {
            DislikeReason reason = new DislikeReason();
            reason.id = REASON_ID_RCMD_REASON;
            reason.name = "推荐原因:" + rcmdReason;
            reasons.add(reason);
        }
        if (upId != 0L) {
            DislikeReason reason = new DislikeReason();
            reason.id = REASON_ID_UP_ID;
            reason.name = "UID:" + upId;
            reasons.add(reason);
        }
        if (!TextUtils.isEmpty(upName)) {
            DislikeReason reason = new DislikeReason();
            reason.id = REASON_ID_UP_NAME;
            reason.name = "UP主:" + upName;
            reasons.add(reason);
        }
        if (!TextUtils.isEmpty(categoryName)) {
            DislikeReason reason = new DislikeReason();
            reason.id = REASON_ID_CATEGORY_NAME;
            reason.name = "分区:" + categoryName;
            reasons.add(reason);
        }
        if (!TextUtils.isEmpty(channelName)) {
            DislikeReason reason = new DislikeReason();
            reason.id = REASON_ID_CHANNEL_NAME;
            reason.name = "频道:" + channelName;
            reasons.add(reason);
        }
        if (!reasons.isEmpty()) {
            List<ThreePointItem> threePoint = item.threePoint;
            if (threePoint == null)
                threePoint = new ArrayList<>();
            ThreePointItem threePointItem = new ThreePointItem();
            threePointItem.title = "漫游屏蔽";
            threePointItem.subtitle = "(本地屏蔽，刷新生效，可前往自定义首页查看)";
            threePointItem.type = "dislike";
            threePointItem.reasons = reasons;
            threePoint.add(threePointItem);
            item.threePoint = threePoint;
        }
    }

    /**
     * 给热门(proto 卡片)补上「漫游屏蔽」三点菜单项。
     *
     * <p>首页那条路径一直有(见 {@link #appendReasons(JSONObject)})，菜单结构完全一致，
     * 选中后由同一个 {@code CardClickProcessor} → {@link #onFeedClick} 接手写入屏蔽列表。
     *
     * <p>热门走的是 proto 卡片({@code SmallCoverV5})，之前**完全没做**这一步 ——
     * 于是热门里刷到不想看的内容没有任何屏蔽入口，只能干瞪眼。
     *
     * <p>宿主侧链路已经确认过：{@code BasicIndexItem(Base)} 会读 {@code getThreePointV2List()}，
     * 再喂给 {@code CardClickProcessor} 渲染菜单。
     *
     * @param card 热门列表里的 proto 卡片。
     */
    public static void appendPopularReasons(Card card) {
        try {
            if (!card.hasSmallCoverV5()) return;
            SmallCoverV5 cover = card.getSmallCoverV5();
            if (!cover.hasBase()) return;
            Base base = cover.getBase();
            // 翻页/刷新时同一张卡会反复经过这里，已经塞过就别重复塞。
            var existing = base.getThreePointV2List();
            for (int i = 0; i < existing.size(); i++)
                if ("漫游屏蔽".equals(existing.get(i).getTitle())) return;

            List<DislikeReason> reasons = collectPopularReasons(cover);
            if (reasons.isEmpty()) return;

            var protoReasons = new ArrayList<com.bapis.bilibili.app.card.v1.DislikeReason>(reasons.size());
            for (DislikeReason reason : reasons)
                protoReasons.add(popularReason(reason.id, reason.name));

            var item = new ThreePointV2();
            item.setTitle("漫游屏蔽");
            item.setSubtitle("(本地屏蔽，刷新生效)");
            item.setType("dislike");
            item.addAllReasons(protoReasons);
            base.addThreePointV2(item);
        } catch (Throwable t) {
            Logger.error(t, () -> "PegasusPatch, append popular reasons failed");
        }
    }

    /**
     * 收集热门卡片可用于屏蔽的维度，返回**宿主 Java 模型**的原因列表。
     *
     * <p>用 Java 模型而不是 proto，是因为热门 ⋮ 面板改造后要复用 {@link #onFeedClick} ——
     * 它接受的正是 {@code com.bilibili.app.comm.list.common.data.DislikeReason}。
     *
     * @param cover 热门卡片。
     * @return 屏蔽原因列表，可能为空。
     */
    private static List<DislikeReason> collectPopularReasons(SmallCoverV5 cover) {
        Base base = cover.getBase();
        List<DislikeReason> reasons = new ArrayList<>();

        String title = base.getTitle();
        if (!TextUtils.isEmpty(title))
            reasons.add(javaReason(REASON_ID_TITLE, "标题:" + title));

        String rcmdReason = cover.getRcmdReasonStyle().getText();
        if (!TextUtils.isEmpty(rcmdReason))
            reasons.add(javaReason(REASON_ID_RCMD_REASON, "推荐原因:" + rcmdReason));

        long upId = base.getThreePointV4().getSharePlane().getAuthorId();
        if (upId != 0L)
            reasons.add(javaReason(REASON_ID_UP_ID, "UID:" + upId));

        String upName = base.getThreePointV4().getSharePlane().getAuthor();
        if (!TextUtils.isEmpty(upName))
            reasons.add(javaReason(REASON_ID_UP_NAME, "UP主:" + upName));

        String categoryName = base.getArgs().getRname();
        if (!TextUtils.isEmpty(categoryName))
            reasons.add(javaReason(REASON_ID_CATEGORY_NAME, "分区:" + categoryName));

        String channelName = base.getArgs().getTname();
        if (!TextUtils.isEmpty(channelName))
            reasons.add(javaReason(REASON_ID_CHANNEL_NAME, "频道:" + channelName));

        return reasons;
    }

    /** 构造宿主 Java 模型的屏蔽原因。名称格式与 {@link #onFeedClick} 的 split 对齐。 */
    private static DislikeReason javaReason(long id, String name) {
        var reason = new DislikeReason();
        reason.id = id;
        reason.name = name;
        return reason;
    }

    /**
     * 构造一条 proto 版的屏蔽原因。名称格式必须与 {@link #onFeedClick} 的
     * {@code name.split(":", 2)[1]} 对齐 —— 冒号是 ASCII 的 {@code U+003A}。
     */
    private static com.bapis.bilibili.app.card.v1.DislikeReason popularReason(long id, String name) {
        var reason = new com.bapis.bilibili.app.card.v1.DislikeReason();
        reason.setId(id);
        reason.setName(name);
        return reason;
    }

    // ==================================================================
    // 热门 ⋮ ←→ 屏蔽面板
    //
    // 宿主事实（8.91.1，取自 smali）：
    //   热门卡片的 ⋮ 走 CardClickProcessor$e.onMenuList(List menus)，
    //   面板项由 CardClickProcessor->X(activity, holder, ThreePointV4Item) 构建 ——
    //   **只读 ThreePointV4（分享 + 稍后再看），完全不读 threePointV2**。
    //   所以漫游原有的「漫游屏蔽」注入在热门没有落点，热门只能分享。
    //
    //   本段把它换掉：开关打开时清空 menus，用 MenuImpl + MenuItemImpl 放屏蔽项；
    //   点击由 CardClickProcessor$e.handleClick(IMenuItem) 回调，按 id 查表，
    //   命中就交给 onFeedClick —— 与首页写的是同一份屏蔽列表。
    // ==================================================================

    /** 菜单项 id 前缀。宿主自己的 itemId 是语义化字符串（"dislike" / "watch_later"…），加前缀避免撞。 */
    private static final String HOT_MENU_ID_PREFIX = "biliroaming_hot_block_";



    /** 当前这次面板里我们放的项: itemId → 屏蔽原因。 */
    private static final Map<String, DislikeReason> sHotMenuItems = new LinkedHashMap<>();

    // ==================================================================
    // 宿主反馈面板（推荐页那一套）的直接调用
    //
    // 推荐页的屏蔽面板**不是**三点菜单，是独立 BottomSheet：
    //   com.bilibili.pegasus.feedbackdialog.FeedbackDialogFragmentV5
    //       extends androidx.fragment.app.BottomSheetDialogFragment   (Jetpack Compose)
    // 入口（从 holders/a0$a->g 的调用点逐参读出）：
    //   com.bilibili.pegasus.feedbackdialog.a->a(
    //       Fragment, int=0, boolean=true, cardGoto, cardType, List<Feedback>, Function1)
    // 面板吃的 List<Feedback> 由 yn3.a->a(ThreePointV5, Function1) 逐组转换。
    //
    // 数据模型（字段名取自各自 writeToParcel 的 iget）：
    //   ThreePointV5      { String style; String title; List items; }
    //   ThreePointV5Item  { long id; String icon; int iconWidth; int iconHeight;
    //                       String iconNight; String title; String subtitle; String type;
    //                       String toast; int value; String extend; boolean isLightPanel;
    //                       List subSections; String link; }
    //   subtitle 就是首页那个括号提示；type 取自 FeedbackItemType(dislike/feedback/watch_later)。
    //
    // 全部走反射：宿主这些类不在集成的编译桩里，反射能少一堆要跟着宿主版本对齐的签名。
    // ==================================================================

    private static final String CLS_FB_ITEM = "com.bilibili.pegasus.feedbackdialog.model.ThreePointV5Item";
    private static final String CLS_FB_SECTION = "com.bilibili.pegasus.feedbackdialog.model.ThreePointV5";
    private static final String CLS_FB_CONVERT = "yn3.a";
    private static final String CLS_FB_ENTRY = "com.bilibili.pegasus.feedbackdialog.a";

    /** kotlin.Unit 的 INSTANCE —— 反射取，避免集成依赖 kotlin-stdlib。 */
    private static Object kotlinUnit() {
        try {
            return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 用**宿主自己的**反馈面板显示屏蔽项，样式与推荐页逐像素一致。
     *
     * @return true 表示已弹出宿主面板；false 表示调不动，调用方应退回自建面板。
     */
    private static boolean showHostFeedbackPanel(Context context, Object threePointV4Item, List<DislikeReason> reasons) {
        try {
            ClassLoader cl = context.getClassLoader();
            Class<?> itemCls = Class.forName(CLS_FB_ITEM, false, cl);
            Class<?> sectionCls = Class.forName(CLS_FB_SECTION, false, cl);
            Class<?> convertCls = Class.forName(CLS_FB_CONVERT, false, cl);
            Class<?> entryCls = Class.forName(CLS_FB_ENTRY, false, cl);
            // Function1 必须**从宿主方法的参数类型里取**，不能 Class.forName：
            // R8 会把集成代码里的字符串 "kotlin.jvm.functions.Function1" 改写成本地
            // kotlin-stdlib 副本被改名后的名字（实测 kofua.A4），而宿主那边保留原名 ——
            // 两个不同的 Class，方法查找必然 NoSuchMethodException。
            java.lang.reflect.Method convert = null;
            for (java.lang.reflect.Method m : convertCls.getDeclaredMethods())
                if ("a".equals(m.getName()) && m.getParameterCount() == 2) { convert = m; break; }
            if (convert == null) throw new NoSuchMethodException("yn3.a.a(ThreePointV5, Function1)");
            Class<?> function1Cls = convert.getParameterTypes()[1];

            Fragment fragment = currentFragment(context);
            if (fragment == null) {
                Logger.debug(() -> "PegasusPatch, no fragment, skip host feedback panel");
                return false;
            }

            Object unit = kotlinUnit();
            Object callback = Proxy.newProxyInstance(cl, new Class<?>[]{function1Cls},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName()) && args != null && args.length == 1)
                            handleHostFeedbackClick(args[0]);
                        return unit;
                    });

            // 每条屏蔽原因 → 一个 ThreePointV5Item
            List<Object> subItems = new ArrayList<>(reasons.size());
            for (DislikeReason reason : reasons)
                subItems.add(newHostFeedbackItem(itemCls, reason.id, reason.name, (int) reason.id));

            // 分组 ThreePointV5 → 面板真正吃的 Feedback
            Object section = sectionCls
                    .getConstructor(String.class, String.class, List.class)
                    .newInstance("grid", "漫游屏蔽", subItems);
            Object feedback = convert.invoke(null, section, callback);

            List<Object> feedbacks = new ArrayList<>(1);
            feedbacks.add(feedback);

            entryCls.getMethod("a", Fragment.class, int.class, boolean.class,
                            String.class, String.class, List.class, function1Cls)
                    .invoke(null, fragment, 0, true, "", "", feedbacks, callback);
            Logger.debug(() -> "PegasusPatch, host feedback panel shown");
            return true;
        } catch (Throwable t) {
            Logger.error(t, () -> "PegasusPatch, host feedback panel failed, falling back to built-in menu");
            return false;
        }
    }

    /** 构造宿主 ThreePointV5Item：14 个参数，顺序与它 writeToParcel 的写出顺序一致。 */
    private static Object newHostFeedbackItem(Class<?> itemCls, long id, String title, int value)
            throws Exception {
        return itemCls.getConstructor(
                        long.class, String.class, int.class, int.class, String.class,
                        String.class, String.class, String.class, String.class,
                        int.class, String.class, boolean.class, List.class, String.class)
                //                    id  icon  iw  ih  iconNight  title  subtitle  type  toast
                .newInstance(id, "", 0, 0, "", title, "", "dislike", "",
                        // value  extend  isLight  subSections  link
                        value, "", true, new ArrayList<>(), "");
    }

    /**
     * 宿主面板的点击回调。
     *
     * <p>宿主自己的实现（{@code holders/z.invoke}）把入参强转成 {@code FeedbackItemType}，
     * 但这里两种形态都认：枚举 / 带 value 的项。拿不准的时候打日志，别静默吞掉。
     */
    private static void handleHostFeedbackClick(Object clicked) {
        try {
            if (clicked == null) return;
            Logger.debug(() -> "PegasusPatch, host feedback click: "
                    + clicked.getClass().getName() + " -> " + clicked);
            Object value = null;
            if (!(clicked instanceof Enum)) {
                value = Reflex.callMethod(clicked, "getValue");
            }
            if (value instanceof Integer) {
                List<DislikeReason> reasons = sHotBlockReasons.get(sHotLastAid);
                if (reasons != null)
                    for (DislikeReason reason : reasons)
                        if (reason.id == (Integer) value) {
                            onFeedClick(reason);
                            return;
                        }
            }
        } catch (Throwable t) {
            Logger.error(t, () -> "PegasusPatch, host feedback click failed");
        }
    }

    /** 当前可用的 Fragment —— 宿主的入口第一个参数要的是 Fragment，不是 Activity。 */
    private static Fragment currentFragment(Context context) {
        try {
            if (!(context instanceof FragmentActivity)) return null;
            List<Fragment> fragments =
                    ((FragmentActivity) context).getSupportFragmentManager().getFragments();
            for (int i = fragments.size() - 1; i >= 0; i--) {
                Fragment fragment = fragments.get(i);
                if (fragment != null && fragment.isAdded()) return fragment;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 最近一次构建面板对应的 aid，点击回调靠它找回候选屏蔽项。 */
    private static long sHotLastAid = 0L;

    /**
     * 热门 ⋮ 面板构建回调。
     *
     * @param menus            宿主待渲染的面板列表，会被我们替换掉。
     * @param context          FragmentActivity。
     * @param threePointV4Item 该卡片的 {@code ThreePointV4Item}，用来定位是哪张卡。
     * @return true 表示已接管（调用方应直接返回，不再走原逻辑）。
     */
    @Keep
    public static boolean onPopularMenuList(List<IMenu> menus, Context context, Object threePointV4Item) {
        try {
            if (menus == null || context == null) return false;
            if (!Settings.PopularShareToBlock.get()) return false;

            List<DislikeReason> reasons = lookupPopularReasons(threePointV4Item);
            if (reasons == null || reasons.isEmpty()) return false;

            // 首选：调**宿主自己的**反馈面板 FeedbackDialogFragmentV5 ——
            // 样式和推荐页完全一致（Compose 底部弹窗 + 2 列格子）。
            //
            // ThreePointV5.style 取 "grid"（FeedbackStyle 的取值真机实测：
            //   LIST=list / GRID=grid / COVER=cover / DEFAULT=default）。
            // 之前填空串时调用不报错但面板静默不渲染，就是卡在这里。
            //
            // 先同步调；**确认真的挂上才清 menus** —— 免得弹不出来还白白丢掉分享面板。
            // 原版面板（FeedbackDialogFragmentV5）已按 style="grid" 完整试过：
            // 调用无异常、menus 也清了，但屏幕上什么都不出现（真机复测）。
            // 这条路先不接 —— 接了就是把分享面板扔掉换个空白。
            // （逆向细节与 FeedbackStyle 实测取值见 _audit/移植进度.md r16。）
            showBuiltInPanel(menus, context, reasons);
            return true;
        } catch (Throwable t) {
            Logger.error(t, () -> "PegasusPatch, popular menu build failed");
            sHotMenuItems.clear();
            return false;
        }
    }

    /**
     * 自建面板（原版调不动时的回退）。
     *
     * <p>文字**照抄推荐页**：直接用 {@code reason.name}（就是 {@code UP主:xxx} / {@code 分区:xxx} /
     * {@code 标题:xxx} 那套 {@code 前缀:值} 格式），不自创措辞。
     */
    private static void showBuiltInPanel(List<IMenu> menus, Context context, List<DislikeReason> reasons) {
        try {
            sHotMenuItems.clear();
            List<IMenuItem> items = new ArrayList<>(reasons.size());
            for (int i = 0; i < reasons.size(); i++) {
                DislikeReason reason = reasons.get(i);
                String itemId = HOT_MENU_ID_PREFIX + i;
                sHotMenuItems.put(itemId, reason);
                // 宿主构造语义(见 CardClickProcessor->X 的 smali):
                //   MenuItemImpl(Context, itemId:String, iconRes:int, titleRes:int)      ← 标题是**资源 id**
                //   MenuItemImpl(Context, itemId:String, iconRes:int, title:CharSequence) ← 标题是**文本**
                items.add(new MenuItemImpl(
                        context, itemId,
                        android.R.color.transparent,
                        reason.name));
            }
            menus.clear();
            menus.add(new MenuImpl(context).setMenuItems(items));
        } catch (Throwable t) {
            Logger.error(t, () -> "PegasusPatch, built-in popular menu failed");
        }
    }

    /**
     * 先探一下宿主的反馈面板能不能用（类 + Fragment 都在），
     * 免得把分享面板清掉了才发现弹不出来、最后什么都不显示。
     */
    private static boolean canShowHostFeedbackPanel(Context context) {
        try {
            ClassLoader cl = context.getClassLoader();
            Class.forName(CLS_FB_ITEM, false, cl);
            Class.forName(CLS_FB_SECTION, false, cl);
            Class.forName(CLS_FB_CONVERT, false, cl);
            Class.forName(CLS_FB_ENTRY, false, cl);
            return currentFragment(context) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 热门 ⋮ 面板点击回调。
     *
     * @param menuItem         被点的菜单项。
     * @param threePointV4Item 该卡片的 {@code ThreePointV4Item}。
     * @return true 表示是我们的项且已处理（调用方应直接 return true）。
     */
    @Keep
    public static boolean onPopularMenuClick(Object menuItem, Object threePointV4Item) {
        try {
            if (menuItem == null || sHotMenuItems.isEmpty()) return false;
            // 宿主的 IMenuItem#getItemId() 返回 **String**（见 ShareMenuBuilder 的调用点）。
            Object rawId = Reflex.callMethod(menuItem, "getItemId");
            if (!(rawId instanceof String)) return false;

            DislikeReason reason = sHotMenuItems.get((String) rawId);
            if (reason == null) return false;
            boolean handled = onFeedClick(reason);
            sHotMenuItems.clear();
            return handled;
        } catch (Throwable t) {
            Logger.error(t, () -> "PegasusPatch, popular menu click failed");
            return false;
        }
    }

    /**
     * 按 aid 找回该卡片在 {@link #filterPopular} 时收集好的屏蔽项。
     *
     * <p>aid 从 {@code ThreePointV4Item.getSharePlane().getAvId()} 取。
     * 用反射是刻意的 —— 这几个模型类没有引进集成的编译桩，反射能少一份要跟宿主对齐的签名。
     */
    private static List<DislikeReason> lookupPopularReasons(Object threePointV4Item) {
        if (threePointV4Item == null) return null;
        // 宿主的 sharePlane 是**公开字段**，不是 getter ——
        // 见 CardClickProcessor$e.handleClick 的 smali: `iget-object v3, v7, ->sharePlane`。
        // 这里按字段读；再兜一层 getter，万一以后换成方法也不用改。
        Object sharePlane = Reflex.getObjectField(threePointV4Item, "sharePlane");
        if (sharePlane == null) {
            try {
                sharePlane = Reflex.callMethod(threePointV4Item, "getSharePlane");
            } catch (Throwable ignored) {
            }
        }
        if (sharePlane == null) return null;
        Object avId = Reflex.callMethod(sharePlane, "getAvId");
        if (!(avId instanceof Long)) return null;
        sHotLastAid = (Long) avId;
        return sHotBlockReasons.get((Long) avId);
    }

    /** aid → 该卡片的屏蔽项，在 {@link #appendPopularReasons} 时写入。 */
    private static final Map<Long, List<DislikeReason>> sHotBlockReasons = new LinkedHashMap<>();

    /** 热门列表最多同时缓存多少张卡的屏蔽项，避免无限增长。 */
    private static final int HOT_BLOCK_CACHE_LIMIT = 256;

    /**
     * 把某张热门卡片的屏蔽项按 aid 记下来，供 ⋮ 面板点击时查表。
     *
     * @param card 热门卡片。
     */
    private static void cachePopularReasons(Card card) {
        try {
            if (!card.hasSmallCoverV5()) return;
            SmallCoverV5 cover = card.getSmallCoverV5();
            if (!cover.hasBase()) return;
            long aid = cover.getBase().getThreePointV4().getSharePlane().getAid();
            if (aid == 0L) return;
            List<DislikeReason> reasons = collectPopularReasons(cover);
            if (reasons.isEmpty()) return;
            if (sHotBlockReasons.size() >= HOT_BLOCK_CACHE_LIMIT) {
                Iterator<Long> it = sHotBlockReasons.keySet().iterator();
                while (it.hasNext() && sHotBlockReasons.size() >= HOT_BLOCK_CACHE_LIMIT) {
                    it.next();
                    it.remove();
                }
            }
            sHotBlockReasons.put(aid, reasons);
        } catch (Throwable t) {
            Logger.error(t, () -> "PegasusPatch, cache popular reasons failed");
        }
    }

    /**
     * @noinspection SameReturnValue
     */
    private static Unit appendReasons(JSONObject item) {
        String title = item.optString("title");
        String rcmdReason = item.optString("rcmd_reason");
        JSONObject args = item.optJSONObject("args");
        long upId = args != null ? args.optLong("up_id") : 0L;
        String upName = args != null ? args.optString("up_name") : null;
        String categoryName = args != null ? args.optString("rname") : null;
        String channelName = args != null ? args.optString("tname") : null;
        var reasons = new JSONArray();
        if (!TextUtils.isEmpty(title)) {
            var reason = new JSONObject();
            Jsons.putX(reason, "id", REASON_ID_TITLE);
            Jsons.putX(reason, "name", "标题:" + title);
            reasons.put(reason);
        }
        if (!TextUtils.isEmpty(rcmdReason)) {
            var reason = new JSONObject();
            Jsons.putX(reason, "id", REASON_ID_RCMD_REASON);
            Jsons.putX(reason, "name", "推荐原因:" + rcmdReason);
            reasons.put(reason);
        }
        if (upId != 0L) {
            var reason = new JSONObject();
            Jsons.putX(reason, "id", REASON_ID_UP_ID);
            Jsons.putX(reason, "name", "UID:" + upId);
            reasons.put(reason);
        }
        if (!TextUtils.isEmpty(upName)) {
            var reason = new JSONObject();
            Jsons.putX(reason, "id", REASON_ID_UP_NAME);
            Jsons.putX(reason, "name", "UP主:" + upName);
            reasons.put(reason);
        }
        if (!TextUtils.isEmpty(categoryName)) {
            var reason = new JSONObject();
            Jsons.putX(reason, "id", REASON_ID_CATEGORY_NAME);
            Jsons.putX(reason, "name", "分区:" + categoryName);
            reasons.put(reason);
        }
        if (!TextUtils.isEmpty(channelName)) {
            var reason = new JSONObject();
            Jsons.putX(reason, "id", REASON_ID_CHANNEL_NAME);
            Jsons.putX(reason, "name", "频道:" + channelName);
            reasons.put(reason);
        }
        if (reasons.length() > 0) {
            var threePoint = item.optJSONArray("three_point_v2");
            if (threePoint == null) return Unit.INSTANCE;
            var threePointItem = new JSONObject();
            Jsons.putX(threePointItem, "title", "漫游屏蔽");
            Jsons.putX(threePointItem, "subtitle", "(本地屏蔽，刷新生效，可前往自定义首页查看)");
            Jsons.putX(threePointItem, "type", "dislike");
            Jsons.putX(threePointItem, "reasons", reasons);
            threePoint.put(threePointItem);
        }
        return Unit.INSTANCE;
    }

    private static boolean shouldDisableAutoRefresh() {
        if (Settings.HomeDisableAutoRefresh.get())
            return true;
        if (!Versions.ge7_76_0())
            return false;
        var setting = KtUtils.getDeviceSetting(PegasusDeviceWithoutFplocalConfig.class);
        if (setting != null) {
            long value = setting.getAutoRefreshState().getValue();
            return value == 2L || value == 4L;
        }
        return false;
    }

    private static void customPegasusConfig(Config config) {
        float ratio = Float.parseFloat(Settings.PegasusCoverRatio.get());
        if (config != null && ratio != 0f && !Utils.isHd())
            config.smallCoverWhRatio = ratio;
        if (config == null || !shouldDisableAutoRefresh())
            return;
        if (Utils.isPink() || Utils.isBlue() || Utils.isPlay()) {
            // only exist on android and android_i now
            config.autoRefreshTimeByActive = -1L;
            config.autoRefreshTimeByAppear = -1L;
            config.autoRefreshTimeByBehavior = -1;
        }
        if (Utils.isPink() || Utils.isBlue()) {
            // only exist on android now
            config.autoRefreshByBehavior = -1;
        }
        if (Utils.isHd()) {
            // only exist on android_hd now
            config.auto_refresh_time = 0;
        }
    }

    // Only called when ab test `ff_key_use_new_pegasus` not hit
    @Keep
    public static void pegasusHook(GeneralResponse<PegasusFeedResponse> response) {
        var data = response.data;
        if (data == null) return;
        customPegasusConfig(data.config);
        var items = data.items;
        if (items == null || items.isEmpty()) return;
        long playCountLimit = Settings.LowPlayCountLimit.get();
        var shortDurationLimit = Settings.ShortDurationLimit.get();
        int longDurationLimit = Settings.LongDurationLimit.get();
        Set<? extends String> titleSet = Settings.HomeRcmdFilterTitle.get();
        boolean titleRegexMode = Settings.HomeRcmdFilterTitleRegexMode.get();
        List<Pattern> titleRegexes;
        if (titleRegexMode && cachedTitileSet.equals(titleSet))
            titleRegexes = cachedTitleRegexes;
        else if (titleRegexMode) {
            cachedTitileSet = new HashSet<>(titleSet);
            titleRegexes = titleSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedTitleRegexes = titleRegexes;
        } else {
            titleRegexes = Collections.emptyList();
        }
        Set<? extends String> reasonSet = Settings.HomeRcmdFilterReason.get();
        boolean reasonRegexMode = Settings.HomeRcmdFilterReasonRegexMode.get();
        List<Pattern> reasonRegexes;
        if (reasonRegexMode && cachedReasonSet.equals(reasonSet))
            reasonRegexes = cachedReasonRegexes;
        else if (reasonRegexMode) {
            cachedReasonSet = new HashSet<>(reasonSet);
            reasonRegexes = reasonSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedReasonRegexes = reasonRegexes;
        } else {
            reasonRegexes = Collections.emptyList();
        }
        Set<? extends String> upSet = Settings.HomeRcmdFilterUp.get();
        boolean upRegexMode = Settings.HomeRcmdFilterUpRegexMode.get();
        List<Pattern> upRegexes;
        if (upRegexMode && cachedUpSet.equals(upSet))
            upRegexes = cachedUpRegexes;
        else if (upRegexMode) {
            cachedUpSet = new HashSet<>(upSet);
            upRegexes = upSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedUpRegexes = upRegexes;
        } else {
            upRegexes = Collections.emptyList();
        }
        Set<? extends String> uidSet = Settings.HomeRcmdFilterUid.get();
        long[] uidArray = ArrayUtils.toLongArray(uidSet);
        Set<? extends String> categorySet = Settings.HomeRcmdFilterCategory.get();
        Set<? extends String> channelSet = Settings.HomeRcmdFilterChannel.get();
        items.removeIf(item -> {
            var cardGoto = item.cardGoto;
            var cardType = item.cardType;
            var goTo = item.goTo;
            var filterTypes = getFilterTypes();
            if (filterTypes.contains("ad"))
                removeAdBanner(item);
            var typeMatched = false;
            for (var i = 0; i < filterTypes.size(); i++) {
                String type = filterTypes.get(i);
                if ((!TextUtils.isEmpty(cardGoto) && cardGoto.contains(type))
                        || (!TextUtils.isEmpty(cardType) && cardType.contains(type))
                        || (!TextUtils.isEmpty(goTo) && goTo.contains(type))) {
                    typeMatched = true;
                    break;
                } else if ("av".equals(cardGoto) && "cannot_play".equals(type)
                        && (item instanceof BasePlayerItem playerItem) && playerItem.canPlay != 1) {
                    typeMatched = true;
                    break;
                }
            }
            return typeMatched || isLowPlayCountVideo(item, playCountLimit)
                    || isDurationInvalidVideo(item, shortDurationLimit, longDurationLimit)
                    || isContainsBlockKwdVideo(item,
                    titleRegexMode, titleSet, titleRegexes,
                    reasonRegexMode, reasonSet, reasonRegexes,
                    upRegexMode, upSet, upRegexes,
                    uidArray, categorySet, channelSet
            );
        });
        int column = data.config != null ? data.config.column : 2;
        align(items, column);
        items.forEach(PegasusPatch::appendReasons);
    }

    private static void align(ArrayList<BasicIndexItem> items, int column) {
        try {
            align0(items, column);
        } catch (Throwable t) {
            // 卡片模型类变动时放弃对齐, 不影响列表内容
        }
    }

    private static void align0(ArrayList<BasicIndexItem> items, int column) {
        if (Utils.isHd() || column % 2 != 0) return;
        int smallCardCount = 0;
        int lastSmallCardIndex = -1;
        for (int i = items.size() - 1; i >= 0; i--) {
            BasicIndexItem item = items.get(i);
            String cardType = item.cardType;
            if (cardType == null) cardType = "";
            long adCardType = -1;
            FeedAdInfo adInfo = item.adInfo;
            if (adInfo != null)
                adCardType = adInfo.getFeedInfoCardType();
            boolean isLargeCard = isLargeCard(cardType, adCardType);
            if (lastSmallCardIndex == -1 && !isLargeCard)
                lastSmallCardIndex = i;
            smallCardCount += (isLargeCard ? 2 : 1);
        }
        if (smallCardCount % 2 != 0)
            items.remove(lastSmallCardIndex);
    }

    private static boolean isLargeCard(String cardType, long adCardType) {
        return cardType.startsWith("banner_v8")
                || cardType.startsWith("large_cover")
                || cardType.startsWith("notify_tunnel")
                || cardType.startsWith("select")
                // see com.bilibili.ad.adview.pegasus.holders.AdHolders
                || (cardType.startsWith("cm")
                && (adCardType == 27 || adCardType == 41 || adCardType == 42
                || adCardType == 44 || adCardType == 54 || adCardType == 57
                || adCardType == 74 || adCardType == 87 || adCardType == 88
                || adCardType == 100 || adCardType == 98 || adCardType == 101
                || adCardType == 103 || adCardType == 129 || adCardType == 133
                || adCardType == 134 || adCardType == 136));
    }

    public static void pegasusHook(JSONObject data) throws JSONException {
        JSONObject config = data.optJSONObject("config");
        float ratio = Float.parseFloat(Settings.PegasusCoverRatio.get());
        if (config != null && ratio != 0f)
            config.put("small_cover_wh_ratio", ratio);
        if (config != null && shouldDisableAutoRefresh()) {
            config.put("auto_refresh_time", 0);
            config.put("auto_refresh_time_by_appear", -1L);
            config.put("auto_refresh_time_by_active", -1L);
            config.put("auto_refresh_time_by_behavior", -1);
            config.put("auto_refresh_by_behavior", -1);
        }
        JSONArray items = data.optJSONArray("items");
        if (items == null || items.length() == 0) return;
        long playCountLimit = Settings.LowPlayCountLimit.get();
        var shortDurationLimit = Settings.ShortDurationLimit.get();
        int longDurationLimit = Settings.LongDurationLimit.get();
        Set<? extends String> titleSet = Settings.HomeRcmdFilterTitle.get();
        boolean titleRegexMode = Settings.HomeRcmdFilterTitleRegexMode.get();
        List<Pattern> titleRegexes;
        if (titleRegexMode && cachedTitileSet.equals(titleSet))
            titleRegexes = cachedTitleRegexes;
        else if (titleRegexMode) {
            cachedTitileSet = new HashSet<>(titleSet);
            titleRegexes = titleSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedTitleRegexes = titleRegexes;
        } else {
            titleRegexes = Collections.emptyList();
        }
        Set<? extends String> reasonSet = Settings.HomeRcmdFilterReason.get();
        boolean reasonRegexMode = Settings.HomeRcmdFilterReasonRegexMode.get();
        List<Pattern> reasonRegexes;
        if (reasonRegexMode && cachedReasonSet.equals(reasonSet))
            reasonRegexes = cachedReasonRegexes;
        else if (reasonRegexMode) {
            cachedReasonSet = new HashSet<>(reasonSet);
            reasonRegexes = reasonSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedReasonRegexes = reasonRegexes;
        } else {
            reasonRegexes = Collections.emptyList();
        }
        Set<? extends String> upSet = Settings.HomeRcmdFilterUp.get();
        boolean upRegexMode = Settings.HomeRcmdFilterUpRegexMode.get();
        List<Pattern> upRegexes;
        if (upRegexMode && cachedUpSet.equals(upSet))
            upRegexes = cachedUpRegexes;
        else if (upRegexMode) {
            cachedUpSet = new HashSet<>(upSet);
            upRegexes = upSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedUpRegexes = upRegexes;
        } else {
            upRegexes = Collections.emptyList();
        }
        Set<? extends String> uidSet = Settings.HomeRcmdFilterUid.get();
        long[] uidArray = ArrayUtils.toLongArray(uidSet);
        Set<? extends String> categorySet = Settings.HomeRcmdFilterCategory.get();
        Set<? extends String> channelSet = Settings.HomeRcmdFilterChannel.get();
        Jsons.removeIf(items, (item) -> {
            var cardType = item.optString("card_type");
            var cardGoto = item.optString("card_goto");
            var goTo = item.optString("goto");
            var filterTypes = getFilterTypes();
            if ("banner".equals(cardGoto) && filterTypes.contains("ad")) {
                JSONArray bannerItems = item.optJSONArray("banner_item");
                if (bannerItems != null && bannerItems.length() > 0)
                    Jsons.removeIf(bannerItems, (b) -> "ad".equals(b.optString("type")));
            }
            var typeMatched = false;
            for (var i = 0; i < filterTypes.size(); i++) {
                String type = filterTypes.get(i);
                if ((!TextUtils.isEmpty(cardGoto) && cardGoto.contains(type))
                        || (!TextUtils.isEmpty(cardType) && cardType.contains(type))
                        || (!TextUtils.isEmpty(goTo) && goTo.contains(type))) {
                    typeMatched = true;
                    break;
                } else if ("av".equals(cardGoto) && "cannot_play".equals(type) && item.optInt("can_play") != 1) {
                    typeMatched = true;
                    break;
                }
            }
            return typeMatched || isLowPlayCountVideo(item, playCountLimit)
                    || isDurationInvalidVideo(item, shortDurationLimit, longDurationLimit)
                    || isContainsBlockKwdVideo(item,
                    titleRegexMode, titleSet, titleRegexes,
                    reasonRegexMode, reasonSet, reasonRegexes,
                    upRegexMode, upSet, upRegexes,
                    uidArray, categorySet, channelSet
            );
        });
        int column = config != null ? config.optInt("column", 2) : 2;
        align(items, column);
        Jsons.forEach(items, PegasusPatch::appendReasons);
    }

    private static void align(JSONArray items, int column) throws JSONException {
        if (Utils.isHd() || column % 2 != 0) return;
        int smallCardCount = 0;
        int lastSmallCardIndex = -1;
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject item = items.getJSONObject(i);
            String cardType = item.optString("card_type");
            long adCardType = -1;
            JSONObject adInfo = item.optJSONObject("ad_info");
            if (adInfo != null)
                adCardType = adInfo.optLong("card_type", -1);
            boolean isLargeCard = isLargeCard(cardType, adCardType);
            if (lastSmallCardIndex == -1 && !isLargeCard)
                lastSmallCardIndex = i;
            smallCardCount += (isLargeCard ? 2 : 1);
        }
        if (smallCardCount % 2 != 0)
            items.remove(lastSmallCardIndex);
    }

    public static void filterViewRelates(ViewReply viewReply) {
        if (Settings.RemoveRelateNothing.get()) {
            viewReply.clearRelates();
            viewReply.clearPagination();
            return;
        }
        List<Integer> indexes = getToRemoveRelateIndexes(viewReply.getRelatesList());
        for (int i = indexes.size() - 1; i >= 0; i--)
            viewReply.removeRelates(indexes.get(i));
    }

    public static void filterViewUniteRelates(Module module, BizType bizType) {
        if (Settings.RemoveRelateNothing.get()) {
            module.clearRelates();
            return;
        }
        Relates relates = module.getRelates();
        List<Integer> indexes = getToRemoveRelateCardIndexes(relates.getCardsList(), bizType);
        for (int i = indexes.size() - 1; i >= 0; i--)
            relates.removeCards(indexes.get(i));
    }

    public static void filterRelatesFeed(RelatesFeedReply feedReply) {
        List<Integer> indexes = getToRemoveRelateIndexes(feedReply.getListList());
        for (int i = indexes.size() - 1; i >= 0; i--)
            feedReply.removeList(indexes.get(i));
    }

    public static void filterRelatesFeedUnite(com.bapis.bilibili.app.viewunite.v1.RelatesFeedReply feedReply) {
        List<Integer> indexes = getToRemoveRelateCardIndexes(feedReply.getRelatesList(), null);
        for (int i = indexes.size() - 1; i >= 0; i--)
            feedReply.removeRelates(indexes.get(i));
    }

    public static void filterPopular(PopularReply reply) {
        List<Card> cards = reply.getItemsList();
        // 先给每张卡挂上「漫游屏蔽」菜单项 + 把屏蔽项按 aid 存起来（⋮ 面板要用），再做过滤。
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            appendPopularReasons(card);
            cachePopularReasons(card);
        }
        List<Integer> indexes = getToRemovePopularIndexes(cards);
        for (int i = indexes.size() - 1; i >= 0; i--)
            reply.removeItems(indexes.get(i));
    }

    public static List<Integer> getToRemoveRelateIndexes(List<Relate> relates) {
        boolean removeRelatePromote = Settings.RemoveRelatePromote.get();
        boolean removeRelateOnlyAv = Settings.RemoveRelateOnlyAv.get();
        boolean applyToVideo = Settings.HomeFilterApplyToVideo.get();
        long playCountLimit = Settings.LowPlayCountLimit.get();
        int shortDurationLimit = Settings.ShortDurationLimit.get();
        int longDurationLimit = Settings.LongDurationLimit.get();
        Set<? extends String> titleSet = Settings.HomeRcmdFilterTitle.get();
        boolean titleRegexMode = Settings.HomeRcmdFilterTitleRegexMode.get();
        List<Pattern> titleRegexes;
        if (titleRegexMode && cachedTitileSet.equals(titleSet))
            titleRegexes = cachedTitleRegexes;
        else if (titleRegexMode) {
            cachedTitileSet = new HashSet<>(titleSet);
            titleRegexes = titleSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedTitleRegexes = titleRegexes;
        } else {
            titleRegexes = Collections.emptyList();
        }
        Set<? extends String> reasonSet = Settings.HomeRcmdFilterReason.get();
        boolean reasonRegexMode = Settings.HomeRcmdFilterReasonRegexMode.get();
        List<Pattern> reasonRegexes;
        if (reasonRegexMode && cachedReasonSet.equals(reasonSet))
            reasonRegexes = cachedReasonRegexes;
        else if (reasonRegexMode) {
            cachedReasonSet = new HashSet<>(reasonSet);
            reasonRegexes = reasonSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedReasonRegexes = reasonRegexes;
        } else {
            reasonRegexes = Collections.emptyList();
        }
        Set<? extends String> upSet = Settings.HomeRcmdFilterUp.get();
        boolean upRegexMode = Settings.HomeRcmdFilterUpRegexMode.get();
        List<Pattern> upRegexes;
        if (upRegexMode && cachedUpSet.equals(upSet))
            upRegexes = cachedUpRegexes;
        else if (upRegexMode) {
            cachedUpSet = new HashSet<>(upSet);
            upRegexes = upSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedUpRegexes = upRegexes;
        } else {
            upRegexes = Collections.emptyList();
        }
        Set<? extends String> uidSet = Settings.HomeRcmdFilterUid.get();
        long[] uidArray = ArrayUtils.toLongArray(uidSet);
        List<Integer> idxList = new ArrayList<>();
        for (int i = 0; i < relates.size(); i++) {
            Relate relate = relates.get(i);
            if (isPromoteRelate(relate, removeRelatePromote)
                    || isNotAvRelate(relate, removeRelateOnlyAv)
                    || (applyToVideo && (isLowPlayCountRelate(relate, playCountLimit)
                    || isDurationInvalidRelate(relate, shortDurationLimit, longDurationLimit)
                    || isContainsBlockKwdRelate(relate, titleRegexMode, titleSet, titleRegexes, reasonRegexMode, reasonSet, reasonRegexes, upRegexMode, upSet, upRegexes, uidArray))))
                idxList.add(i);
        }
        return idxList;
    }

    public static List<Integer> getToRemoveRelateCardIndexes(List<RelateCard> relateCards, BizType bizType) {
        boolean removeRelatePromote = Settings.RemoveRelatePromote.get();
        boolean removeRelateOnlyAv = Settings.RemoveRelateOnlyAv.get();
        boolean removeRelateCharge = Settings.RemoveRelateCharge.get();
        boolean applyToVideo = Settings.HomeFilterApplyToVideo.get();
        long playCountLimit = Settings.LowPlayCountLimit.get();
        int shortDurationLimit = Settings.ShortDurationLimit.get();
        int longDurationLimit = Settings.LongDurationLimit.get();
        Set<? extends String> titleSet = Settings.HomeRcmdFilterTitle.get();
        boolean titleRegexMode = Settings.HomeRcmdFilterTitleRegexMode.get();
        List<Pattern> titleRegexes;
        if (titleRegexMode && cachedTitileSet.equals(titleSet))
            titleRegexes = cachedTitleRegexes;
        else if (titleRegexMode) {
            cachedTitileSet = new HashSet<>(titleSet);
            titleRegexes = titleSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedTitleRegexes = titleRegexes;
        } else {
            titleRegexes = Collections.emptyList();
        }
        Set<? extends String> reasonSet = Settings.HomeRcmdFilterReason.get();
        boolean reasonRegexMode = Settings.HomeRcmdFilterReasonRegexMode.get();
        List<Pattern> reasonRegexes;
        if (reasonRegexMode && cachedReasonSet.equals(reasonSet))
            reasonRegexes = cachedReasonRegexes;
        else if (reasonRegexMode) {
            cachedReasonSet = new HashSet<>(reasonSet);
            reasonRegexes = reasonSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedReasonRegexes = reasonRegexes;
        } else {
            reasonRegexes = Collections.emptyList();
        }
        Set<? extends String> upSet = Settings.HomeRcmdFilterUp.get();
        boolean upRegexMode = Settings.HomeRcmdFilterUpRegexMode.get();
        List<Pattern> upRegexes;
        if (upRegexMode && cachedUpSet.equals(upSet))
            upRegexes = cachedUpRegexes;
        else if (upRegexMode) {
            cachedUpSet = new HashSet<>(upSet);
            upRegexes = upSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedUpRegexes = upRegexes;
        } else {
            upRegexes = Collections.emptyList();
        }
        Set<? extends String> uidSet = Settings.HomeRcmdFilterUid.get();
        long[] uidArray = ArrayUtils.toLongArray(uidSet);
        List<Integer> idxList = new ArrayList<>();
        for (int i = 0; i < relateCards.size(); i++) {
            RelateCard relate = relateCards.get(i);
            if (isPromoteRelateUnite(relate, removeRelatePromote)
                    || isNotAvRelateUnite(relate, removeRelateOnlyAv, bizType)
                    || (removeRelateCharge && relate.getBasicInfo().getReportFlowData().contains("chg_plt_up"))
                    || (applyToVideo && (isLowPlayCountRelateUnite(relate, playCountLimit)
                    || isDurationInvalidRelateUnite(relate, shortDurationLimit, longDurationLimit)
                    || isContainsBlockKwdRelateUnite(relate, titleRegexMode, titleSet, titleRegexes, reasonRegexMode, reasonSet, reasonRegexes, upRegexMode, upSet, upRegexes, uidArray))))
                idxList.add(i);
        }
        return idxList;
    }

    public static List<Integer> getToRemovePopularIndexes(List<Card> cards) {
        boolean blockTopEntrance = Settings.BlockPopularTopEntrance.get();
        boolean blockTopicList = Settings.BlockPopularTopicList.get();
        boolean blockRcmdUp = Settings.BlockPopularRcmdUp.get();
        boolean blockLive = Settings.BlockPopularLive.get();
        boolean filterApplyToPopular = Settings.HomeFilterApplyToPopular.get();
        long playCountLimit = Settings.LowPlayCountLimit.get();
        int shortDurationLimit = Settings.ShortDurationLimit.get();
        int longDurationLimit = Settings.LongDurationLimit.get();
        Set<? extends String> titleSet = Settings.HomeRcmdFilterTitle.get();
        boolean titleRegexMode = Settings.HomeRcmdFilterTitleRegexMode.get();
        List<Pattern> titleRegexes;
        if (titleRegexMode && cachedTitileSet.equals(titleSet))
            titleRegexes = cachedTitleRegexes;
        else if (titleRegexMode) {
            cachedTitileSet = new HashSet<>(titleSet);
            titleRegexes = titleSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedTitleRegexes = titleRegexes;
        } else {
            titleRegexes = Collections.emptyList();
        }
        Set<? extends String> reasonSet = Settings.HomeRcmdFilterReason.get();
        boolean reasonRegexMode = Settings.HomeRcmdFilterReasonRegexMode.get();
        List<Pattern> reasonRegexes;
        if (reasonRegexMode && cachedReasonSet.equals(reasonSet))
            reasonRegexes = cachedReasonRegexes;
        else if (reasonRegexMode) {
            cachedReasonSet = new HashSet<>(reasonSet);
            reasonRegexes = reasonSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedReasonRegexes = reasonRegexes;
        } else {
            reasonRegexes = Collections.emptyList();
        }
        Set<? extends String> upSet = Settings.HomeRcmdFilterUp.get();
        boolean upRegexMode = Settings.HomeRcmdFilterUpRegexMode.get();
        List<Pattern> upRegexes;
        if (upRegexMode && cachedUpSet.equals(upSet))
            upRegexes = cachedUpRegexes;
        else if (upRegexMode) {
            cachedUpSet = new HashSet<>(upSet);
            upRegexes = upSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedUpRegexes = upRegexes;
        } else {
            upRegexes = Collections.emptyList();
        }
        Set<? extends String> uidSet = Settings.HomeRcmdFilterUid.get();
        long[] uidArray = ArrayUtils.toLongArray(uidSet);
        // 分区 / 分类 —— 首页那条路径一直是支持的, 热门这条路径原来**整个漏掉了**,
        // 结果就是"屏蔽分区 / 屏蔽分类"在热门区完全不生效。
        Set<? extends String> categorySet = Settings.HomeRcmdFilterCategory.get();
        Set<? extends String> channelSet = Settings.HomeRcmdFilterChannel.get();
        List<Integer> idxList = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            if (blockTopEntrance && card.hasPopularTopEntrance()) {
                idxList.add(i);
                continue;
            }
            if (blockTopicList) {
                var hasTopicList = false;
                try {
                    hasTopicList = card.hasTopicList();
                } catch (Throwable ignored) {
                }
                if (hasTopicList) {
                    idxList.add(i);
                    continue;
                }
            }
            if (blockRcmdUp && card.hasRcmdOneItem() && card.getRcmdOneItem().getBase().getCardGoto().startsWith("up_rcmd")) {
                idxList.add(i);
                continue;
            }
            if (blockLive && card.hasSmallCoverV5() && card.getSmallCoverV5().getBase().getCardGoto().startsWith("live")) {
                idxList.add(i);
                continue;
            }
            if (!filterApplyToPopular) continue;
            if (card.hasSmallCoverV5()) {
                SmallCoverV5 cover = card.getSmallCoverV5();
                if (isLowPlayCountPopular(cover, playCountLimit)
                        || isDurationInvalidPopular(cover, shortDurationLimit, longDurationLimit)
                        || isContainsBlockKwdPopular(cover, titleRegexMode, titleSet, titleRegexes, reasonRegexMode, reasonSet, reasonRegexes, upRegexMode, upSet, upRegexes, uidArray, categorySet, channelSet))
                    idxList.add(i);
            }
        }
        return idxList;
    }

    public static boolean isLowPlayCountPopular(SmallCoverV5 cover, long playCountLimit) {
        if (playCountLimit == 0) return false;
        String rightDesc2 = cover.getRightDesc2();
        if (TextUtils.isEmpty(rightDesc2)) return false;
        long playCount = toPlayCount2(rightDesc2);
        if (playCount == -1L) return false;
        return playCount < playCountLimit;
    }

    public static boolean isDurationInvalidPopular(SmallCoverV5 cover, long shortDuration, long longDuration) {
        if (shortDuration == 0 && longDuration == 0)
            return false;
        String coverRightText1 = cover.getCoverRightText1();
        int duration = toDuration(coverRightText1);
        if (duration == -1L) return false;
        if (longDuration != 0 && duration > longDuration) return true;
        return shortDuration != 0 && duration < shortDuration;
    }

    public static boolean isContainsBlockKwdPopular(
            SmallCoverV5 cover,
            boolean titleRegexMode,
            Set<? extends String> titleSet,
            List<Pattern> titleRegexes,
            boolean reasonRegexMode,
            Set<? extends String> reasonSet,
            List<Pattern> reasonRegexes,
            boolean upRegexMode,
            Set<? extends String> upSet,
            List<Pattern> upRegexes,
            long[] uidArray,
            Set<? extends String> categorySet,
            Set<? extends String> channelSet) {
        if (!titleSet.isEmpty()) {
            String title = cover.getBase().getTitle();
            if (titleRegexMode && !TextUtils.isEmpty(title)) {
                for (int i = 0; i < titleRegexes.size(); i++)
                    if (titleRegexes.get(i).matcher(title).find())
                        return true;
            } else if (!TextUtils.isEmpty(title))
                for (String s : titleSet)
                    if (title.contains(s))
                        return true;
        }

        if (uidArray.length > 0) {
            long upId = cover.getBase().getThreePointV4().getSharePlane().getAuthorId();
            if (upId != 0L && ArrayUtils.contains(uidArray, upId))
                return true;
        }

        if (!upSet.isEmpty()) {
            String upName = cover.getBase().getThreePointV4().getSharePlane().getAuthor();
            if (upRegexMode && !TextUtils.isEmpty(upName)) {
                for (int i = 0; i < upRegexes.size(); i++)
                    if (upRegexes.get(i).matcher(upName).find())
                        return true;
            } else if (!TextUtils.isEmpty(upName))
                for (String s : upSet)
                    if (upName.contains(s))
                        return true;
        }

        // 分区 —— 对应首页路径里的 `args.rname`
        if (categorySet != null && !categorySet.isEmpty()) {
            String category = cover.getBase().getArgs().getRname();
            if (!TextUtils.isEmpty(category))
                for (String s : categorySet)
                    if (category.contains(s))
                        return true;
        }

        // 分类 —— 对应首页路径里的 `args.tname`
        if (channelSet != null && !channelSet.isEmpty()) {
            String channel = cover.getBase().getArgs().getTname();
            if (!TextUtils.isEmpty(channel))
                for (String s : channelSet)
                    if (channel.contains(s))
                        return true;
        }

        if (!reasonSet.isEmpty()) {
            String reason = cover.getRcmdReasonStyle().getText();
            if (reasonRegexMode && !TextUtils.isEmpty(reason)) {
                for (int i = 0; i < reasonRegexes.size(); i++)
                    if (reasonRegexes.get(i).matcher(reason).find())
                        return true;
            } else if (!TextUtils.isEmpty(reason)) {
                for (String s : reasonSet)
                    if (reason.contains(s))
                        return true;
            }
        }

        return false;
    }

    public static void filterStory(StoryFeedResponse storyFeedResponse) {
        Set<? extends String> filterTypes = Settings.FilterStory.get();
        boolean applyToStory = Settings.HomeFilterApplyToStory.get();
        boolean removeChargeButton = Settings.RemoveChargeButton.get();
        if (filterTypes.isEmpty() && !applyToStory && !removeChargeButton)
            return;
        long playCountLimit = Settings.LowPlayCountLimit.get();
        int shortDurationLimit = Settings.ShortDurationLimitStory.get();
        int longDurationLimit = Settings.LongDurationLimitStory.get();
        Set<? extends String> titleSet = Settings.HomeRcmdFilterTitle.get();
        boolean titleRegexMode = Settings.HomeRcmdFilterTitleRegexMode.get();
        List<Pattern> titleRegexes;
        if (titleRegexMode && cachedTitileSet.equals(titleSet))
            titleRegexes = cachedTitleRegexes;
        else if (titleRegexMode) {
            cachedTitileSet = new HashSet<>(titleSet);
            titleRegexes = titleSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedTitleRegexes = titleRegexes;
        } else {
            titleRegexes = Collections.emptyList();
        }
        Set<? extends String> upSet = Settings.HomeRcmdFilterUp.get();
        boolean upRegexMode = Settings.HomeRcmdFilterUpRegexMode.get();
        List<Pattern> upRegexes;
        if (upRegexMode && cachedUpSet.equals(upSet))
            upRegexes = cachedUpRegexes;
        else if (upRegexMode) {
            cachedUpSet = new HashSet<>(upSet);
            upRegexes = upSet.stream().map(Pattern::compile).collect(Collectors.toList());
            cachedUpRegexes = upRegexes;
        } else {
            upRegexes = Collections.emptyList();
        }
        Set<? extends String> uidSet = Settings.HomeRcmdFilterUid.get();
        long[] uidArray = ArrayUtils.toLongArray(uidSet);
        List<StoryDetail> items = storyFeedResponse.getItems();
        if (items == null || items.isEmpty()) return;
        items.removeIf(story -> {
            if (removeChargeButton) {
                StoryDetail.Owner owner = story.getOwner();
                if (owner != null) {
                    StoryDetail.Charge charge = owner.getCharge();
                    if (charge != null)
                        charge.setShow(false);
                }
            }
            return isTypeMatchedStory(story, filterTypes)
                    || (applyToStory && (isLowPlayCountStory(story, playCountLimit)
                    || isDurationInvalidStory(story, shortDurationLimit, longDurationLimit)
                    || isUidBlockedStory(story, uidArray)
                    || isUpBlockedStory(story, upSet, upRegexes, upRegexMode)
                    || isTitleBlockedStory(story, titleSet, titleRegexes, titleRegexMode)));
        });
    }

    private static boolean isTypeMatchedStory(StoryDetail story, Set<? extends String> filterTypes) {
        if (filterTypes.isEmpty()) return false;
        String aGoto = story.getGoto();
        if (TextUtils.isEmpty(aGoto)) return false;
        for (String type : filterTypes)
            if (aGoto.contains(type))
                return true;
        return false;
    }

    private static boolean isLowPlayCountStory(StoryDetail story, long playCountLimit) {
        if (playCountLimit == 0) return false;
        StoryDetail.Stat stat = story.getStat();
        return stat != null && stat.getView() < playCountLimit;
    }

    private static boolean isDurationInvalidStory(StoryDetail story, int shortDuration, int longDuration) {
        if (shortDuration == 0 && longDuration == 0)
            return false;
        long duration = story.getDuration();
        if (shortDuration != 0 && duration < shortDuration)
            return true;
        return longDuration != 0 && duration > longDuration;
    }

    private static boolean isTitleBlockedStory(StoryDetail story, Set<? extends String> titles, List<Pattern> titleRegexes, boolean regexMode) {
        if (titles.isEmpty()) return false;
        String title = story.getTitle();
        if (TextUtils.isEmpty(title)) return false;
        if (regexMode) {
            for (int i = 0; i < titleRegexes.size(); i++) {
                if (titleRegexes.get(i).matcher(title).find())
                    return true;
            }
        } else {
            for (String s : titles) {
                if (title.contains(s))
                    return true;
            }
        }
        return false;
    }

    private static boolean isUidBlockedStory(StoryDetail story, long[] uids) {
        if (uids.length == 0) return false;
        StoryDetail.Owner owner = story.getOwner();
        if (owner == null) return false;
        long mid = owner.getMid();
        if (mid <= 0) return false;
        return ArrayUtils.contains(uids, mid);
    }

    private static boolean isUpBlockedStory(StoryDetail story, Set<? extends String> ups, List<Pattern> upRegexes, boolean regexMode) {
        if (ups.isEmpty()) return false;
        StoryDetail.Owner owner = story.getOwner();
        if (owner == null) return false;
        String name = owner.getName();
        if (TextUtils.isEmpty(name)) return false;
        if (regexMode) {
            for (int i = 0; i < upRegexes.size(); i++) {
                if (upRegexes.get(i).matcher(name).find())
                    return true;
            }
        } else {
            for (String s : ups) {
                if (name.contains(s))
                    return true;
            }
        }
        return false;
    }

    @Keep
    public static boolean onFeedClick(DislikeReason reason) {
        if (reason == null || !ArrayUtils.contains(blockReasonIds, reason.id))
            return false;
        long id = reason.id;
        String name = reason.name;
        if (TextUtils.isEmpty(name))
            return false;
        String value = name.split(":", 2)[1];
        String validValue;
        if (id == REASON_ID_TITLE) {
            validValue = Settings.HomeRcmdFilterTitleRegexMode.get()
                    ? Pattern.quote(value) : value;
            Settings.HomeRcmdFilterTitle.append(validValue);
        } else if (id == REASON_ID_RCMD_REASON) {
            validValue = Settings.HomeRcmdFilterReasonRegexMode.get()
                    ? Pattern.quote(value) : value;
            Settings.HomeRcmdFilterReason.append(validValue);
        } else if (id == REASON_ID_UP_ID) {
            Settings.HomeRcmdFilterUid.append(value);
        } else if (id == REASON_ID_UP_NAME) {
            validValue = Settings.HomeRcmdFilterUpRegexMode.get()
                    ? Pattern.quote(value) : value;
            Settings.HomeRcmdFilterUp.append(validValue);
        } else if (id == REASON_ID_CATEGORY_NAME) {
            Settings.HomeRcmdFilterCategory.append(value);
        } else if (id == REASON_ID_CHANNEL_NAME) {
            Settings.HomeRcmdFilterChannel.append(value);
        }
        // 添加成功
        Toasts.showShortWithId("biliroaming_add_success");
        return true;
    }
}
