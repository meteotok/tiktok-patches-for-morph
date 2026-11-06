package app.morphe.extension.tiktok.feedfilter;

import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeExtKt;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last line of defence against ads: hooks at the shared vertical video pager store
 * (For You, Following, profile/search/sound detail, Series episodes), the cold start
 * splash ad and search result cards.
 *
 * Only items that carry TikTok's own ad payload are removed (isAd / isSoftAd / pseudo ad),
 * so creator videos are never dropped here. Every hook fails open: on any error the
 * original data is kept.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class AdBlock {
    private static final AdsFilter ADS = new AdsFilter();

    private AdBlock() {}

    public static boolean enabled() {
        try {
            return ADS.getEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when the item carries an ad payload. */
    public static boolean isHardAd(Object item) {
        if (!(item instanceof Aweme)) return false;
        Aweme aweme = (Aweme) item;
        try {
            if (aweme.isAd() || aweme.isSoftAd()) return true;
        } catch (Throwable ignored) {
        }
        try {
            return AwemeExtKt.isPseudoAd(aweme);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Single-item insertion into the video pager: true means drop it. */
    public static boolean shouldDrop(Object item) {
        return enabled() && isHardAd(item);
    }

    /** List handed to the video pager: returns a copy without ads (or the original). */
    public static List filterList(List items) {
        if (items == null || items.isEmpty() || !enabled()) return items;
        try {
            boolean hasAd = false;
            for (Object item : items) {
                if (isHardAd(item)) {
                    hasAd = true;
                    break;
                }
            }
            if (!hasAd) return items;
            ArrayList kept = new ArrayList(items.size());
            for (Object item : items) {
                if (!isHardAd(item)) kept.add(item);
            }
            // Never leave a pager empty because of us; TikTok then shows stale cached videos.
            return kept.isEmpty() ? items : kept;
        } catch (Throwable ignored) {
            return items;
        }
    }

    /** Removes ads in place from a live pager list (after bulk inserts that bypass the store). */
    public static void purgeInPlace(List items) {
        if (items == null || items.isEmpty() || !enabled()) return;
        try {
            int ads = 0;
            for (Object item : items) if (isHardAd(item)) ads++;
            if (ads == 0 || ads == items.size()) return;
            Iterator iterator = items.iterator();
            while (iterator.hasNext()) {
                if (isHardAd(iterator.next())) iterator.remove();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Cold/warm start splash and TopView ads. */
    public static boolean allowSplashAd(boolean original) {
        return original && !enabled();
    }

    /** Series: never treat an episode as a blocking ad. */
    public static boolean allowBlockingAd(boolean original) {
        return original && !enabled();
    }

    // Search result cards that are ads without Aweme ad payloads.
    private static final String[] SEARCH_AD_FIELDS = {
        "preciseAd", "aiAdCard", "multiAdCard", "brandZoneCard",
    };
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> MISSING_FIELDS = ConcurrentHashMap.newKeySet();

    private static Field findField(Class<?> type, String name) {
        String key = type.getName() + '#' + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;
        if (MISSING_FIELDS.contains(key)) return null;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field found = c.getDeclaredField(name);
                found.setAccessible(true);
                FIELD_CACHE.put(key, found);
                return found;
            } catch (Throwable ignored) {
            }
        }
        MISSING_FIELDS.add(key);
        return null;
    }

    public static boolean isSearchAdCard(Object card) {
        if (card == null) return false;
        try {
            for (String name : SEARCH_AD_FIELDS) {
                Field field = findField(card.getClass(), name);
                if (field != null && field.get(card) != null) return true;
            }
            Field aweme = findField(card.getClass(), "aweme");
            if (aweme != null && isHardAd(aweme.get(card))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
