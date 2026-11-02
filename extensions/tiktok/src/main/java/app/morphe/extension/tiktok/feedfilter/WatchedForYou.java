package app.morphe.extension.tiktok.feedfilter;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeStatistics;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;

/**
 * Last pass over every For You response, after the content and popularity filters.
 * <p>
 * Videos For You already handed out in an earlier response are dropped, so refreshing the feed
 * or reopening the app does not bring back what was already scrolled past. The list of handed
 * out videos survives restarts.
 * <p>
 * A response is never left empty: TikTok treats an empty page as a failed load and falls back to
 * its cached feed, which is where the old videos came from. When the filters remove everything,
 * the least liked new video of the page is kept.
 */
final class WatchedForYou {
    private static final int MAX_REMEMBERED = 5000;
    private static final String FILE_NAME = "prism_watched_for_you.txt";
    /** Marks videos remembered from an earlier app start. */
    private static final int EARLIER_RUN = 0;

    /** Video id to the identity of the response that first handed it out. */
    private static final LinkedHashMap<String, Integer> handedOut = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };
    private static boolean loaded;
    private static boolean savePending;

    private WatchedForYou() {
    }

    static synchronized void apply(FeedItemList feedItemList, List original, List<IFilter> contentFilters) {
        if (feedItemList == null || original == null || original.isEmpty()) {
            return;
        }
        List current = feedItemList.items;
        if (current == null) {
            return;
        }
        load();
        boolean hideWatched = Settings.HIDE_WATCHED_FOR_YOU.get();
        int response = System.identityHashCode(original);

        List kept = new ArrayList(current.size());
        boolean edited = false;
        for (Object container : current) {
            if (hideWatched && container instanceof Aweme && isWatched((Aweme) container, response)) {
                edited = true;
                continue;
            }
            kept.add(container);
        }
        if (kept.isEmpty()) {
            Object fallback = fallback(original, response, contentFilters);
            if (fallback != null) {
                kept.add(fallback);
                edited = true;
            }
        }

        boolean changed = false;
        for (Object container : kept) {
            if (container instanceof Aweme) {
                String aid = ((Aweme) container).getAid();
                if (aid != null && !handedOut.containsKey(aid)) {
                    handedOut.put(aid, response);
                    changed = true;
                }
            }
        }
        if (edited) {
            feedItemList.items = kept;
        }
        if (changed) {
            scheduleSave();
        }
    }

    private static boolean isWatched(Aweme item, int response) {
        String aid = item.getAid();
        if (aid == null || LinkOpenExemption.isExempt(item)) {
            return false;
        }
        Integer firstResponse = handedOut.get(aid);
        // The same response is filtered more than once; only an earlier one counts.
        return firstResponse != null && firstResponse != response;
    }

    /** The least liked plain video of the page, preferring ones not seen before. */
    private static Object fallback(List original, int response, List<IFilter> contentFilters) {
        Object best = null;
        boolean bestNew = false;
        long bestLikes = Long.MAX_VALUE;
        for (Object container : original) {
            if (!(container instanceof Aweme)) {
                continue;
            }
            Aweme item = (Aweme) container;
            if (rejected(item, contentFilters)) {
                continue;
            }
            boolean isNew = !isWatched(item, response);
            AwemeStatistics statistics = item.getStatistics();
            long likes = statistics == null ? Long.MAX_VALUE - 1 : statistics.getDiggCount();
            if (best == null || (isNew && !bestNew) || (isNew == bestNew && likes < bestLikes)) {
                best = container;
                bestNew = isNew;
                bestLikes = likes;
            }
        }
        return best;
    }

    private static boolean rejected(Aweme item, List<IFilter> contentFilters) {
        for (IFilter filter : contentFilters) {
            try {
                if (filter.getEnabled() && filter.getFiltered(item)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // A filter that can't read this item doesn't reject it.
            }
        }
        return false;
    }

    // region storage

    private static File file() {
        Context context = Utils.getContext();
        return context == null ? null : new File(context.getFilesDir(), FILE_NAME);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            File file = file();
            if (file == null || !file.exists()) {
                return;
            }
            byte[] bytes = new byte[(int) Math.min(file.length(), 1 << 20)];
            try (FileInputStream in = new FileInputStream(file)) {
                int read = 0;
                while (read < bytes.length) {
                    int n = in.read(bytes, read, bytes.length - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
            }
            for (String aid : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
                if (!aid.isEmpty()) {
                    handedOut.put(aid.trim(), EARLIER_RUN);
                }
            }
        } catch (Throwable throwable) {
            Logger.printDebug(() -> "Prism: could not read watched videos: " + throwable);
        }
    }

    private static void scheduleSave() {
        if (savePending) {
            return;
        }
        savePending = true;
        Utils.runOnBackgroundThread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                // Save now.
            }
            String text;
            synchronized (WatchedForYou.class) {
                savePending = false;
                StringBuilder builder = new StringBuilder(handedOut.size() * 20);
                for (Iterator<String> it = handedOut.keySet().iterator(); it.hasNext(); ) {
                    builder.append(it.next()).append('\n');
                }
                text = builder.toString();
            }
            try {
                File file = file();
                if (file == null) {
                    return;
                }
                File temp = new File(file.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                if (!temp.renameTo(file)) {
                    temp.delete();
                }
            } catch (Throwable throwable) {
                Logger.printDebug(() -> "Prism: could not save watched videos: " + throwable);
            }
        });
    }

    // endregion
}
