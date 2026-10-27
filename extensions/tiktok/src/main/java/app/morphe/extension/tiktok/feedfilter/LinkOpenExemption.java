package app.morphe.extension.tiktok.feedfilter;

import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ss.android.ugc.aweme.feed.model.Aweme;

/**
 * Keeps a video opened from a link visible even when the like filter would hide it.
 * <p>
 * When TikTok is started by a link, the video id is taken from the link when it has one;
 * short links (vt.tiktok.com) carry no id, so the first video the filter checks right after
 * the link is treated as the linked one, which is the video TikTok puts first.
 */
public final class LinkOpenExemption {
    private static final long WINDOW_MS = 30_000;
    private static final int MAX_REMEMBERED = 32;
    private static final Pattern VIDEO_ID = Pattern.compile("(\\d{15,21})");

    private static final Deque<String> exempt = new ArrayDeque<>();
    private static long pendingSince;
    private static String pendingId;

    private LinkOpenExemption() {
    }

    /** Called from the deep link router's onCreate. */
    public static void onActivity(android.app.Activity activity) {
        try {
            onIntent(activity.getIntent());
        } catch (Throwable ignored) {
            // Never break opening a link.
        }
    }

    /** Called when an activity starts or receives a new intent. */
    public static synchronized void onIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri data = intent.getData();
        if (data == null) {
            return;
        }
        String host = data.getHost() == null ? "" : data.getHost();
        String scheme = data.getScheme() == null ? "" : data.getScheme();
        if (!host.contains("tiktok") && !scheme.startsWith("snssdk") && !scheme.contains("tiktok")
                && !scheme.contains("musically")) {
            return;
        }
        Matcher matcher = VIDEO_ID.matcher(data.toString());
        pendingId = matcher.find() ? matcher.group(1) : null;
        pendingSince = SystemClock.elapsedRealtime();
        if (pendingId != null) {
            remember(pendingId);
        }
    }

    /** True if the like filter must let this video through. */
    public static synchronized boolean isExempt(Aweme item) {
        String aid = item.getAid();
        if (aid == null) {
            return false;
        }
        if (exempt.contains(aid)) {
            return true;
        }
        if (pendingSince != 0 && SystemClock.elapsedRealtime() - pendingSince < WINDOW_MS
                && pendingId == null) {
            // Short link: the first video checked after it is the linked one.
            pendingSince = 0;
            remember(aid);
            return true;
        }
        return false;
    }

    private static void remember(String aid) {
        exempt.remove(aid);
        exempt.addFirst(aid);
        while (exempt.size() > MAX_REMEMBERED) {
            exempt.removeLast();
        }
    }
}
