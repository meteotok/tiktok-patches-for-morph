package app.morphe.extension.tiktok.prism;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Nickname badges handed out from the Admin Panel.
 * <p>
 * Badges come from two places: the local list edited in the Admin Panel (only this phone sees it)
 * and a plain text file in the Prism repository, so everyone running this build sees the
 * badges the repository owner pushes there.
 */
@SuppressWarnings("unused")
public final class PrismBadges {
    public static final String REMOTE_URL =
            "https://raw.githubusercontent.com/meteotok/tiktok-patches-for-morph/main/prism-badges.txt";
    private static final long REMOTE_REFRESH_MS = 6L * 60 * 60 * 1000;

    public enum Type {
        VERIFIED("verified", "Галочка"),
        CROWN("crown", "Корона"),
        STAR("star", "Звезда"),
        HEART("heart", "Сердечко"),
        BOLT("bolt", "Молния"),
        COMET("comet", "Комета");

        public final String key;
        public final String title;

        Type(String key, String title) {
            this.key = key;
            this.title = title;
        }

        @Nullable
        public static Type fromKey(String key) {
            if (key == null) {
                return null;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            for (Type type : values()) {
                if (type.key.equals(normalized)) {
                    return type;
                }
            }
            return null;
        }
    }

    private static volatile Map<String, Type> remote = Collections.emptyMap();
    private static volatile Map<String, Type> local;
    private static volatile String localSource;
    private static volatile boolean remoteRequested;

    private PrismBadges() {
    }

    public static boolean enabled() {
        try {
            return Settings.PRISM_BADGES.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Normalizes a nickname: no leading @, no surrounding blanks, lower case. */
    public static String normalize(CharSequence value) {
        String text = value.toString().trim();
        while (text.startsWith("@")) {
            text = text.substring(1);
        }
        return text.toLowerCase(Locale.ROOT);
    }

    // region storage

    public static Map<String, Type> localBadges() {
        String source = Settings.PRISM_BADGE_LIST.get();
        Map<String, Type> current = local;
        if (current != null && source.equals(localSource)) {
            return current;
        }
        current = Collections.unmodifiableMap(parse(source));
        local = current;
        localSource = source;
        return current;
    }

    public static Map<String, Type> remoteBadges() {
        return remote;
    }

    public static void put(String nickname, Type type) {
        Map<String, Type> badges = new LinkedHashMap<>(localBadges());
        badges.put(normalize(nickname), type);
        save(badges);
    }

    public static void remove(String nickname) {
        Map<String, Type> badges = new LinkedHashMap<>(localBadges());
        badges.remove(normalize(nickname));
        save(badges);
    }

    private static void save(Map<String, Type> badges) {
        Settings.PRISM_BADGE_LIST.save(serialize(badges));
        PrismUi.badgesChanged();
    }

    /** One line per badge, the same format as the repository file. */
    public static String serialize(Map<String, Type> badges) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Type> entry : badges.entrySet()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.getKey()).append(" = ").append(entry.getValue().key);
        }
        return builder.toString();
    }

    /** Parses "nickname = type" lines; '#' starts a comment. */
    public static Map<String, Type> parse(String source) {
        Map<String, Type> badges = new LinkedHashMap<>();
        if (source == null) {
            return badges;
        }
        for (String rawLine : source.split("[\\n;]")) {
            String line = rawLine;
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String nickname = normalize(line.substring(0, separator));
            Type type = Type.fromKey(line.substring(separator + 1));
            if (!nickname.isEmpty() && type != null) {
                badges.put(nickname, type);
            }
        }
        return badges;
    }

    @Nullable
    public static Type lookup(CharSequence text) {
        if (text == null || text.length() == 0 || text.length() > 40) {
            return null;
        }
        String key = normalize(text);
        if (key.isEmpty()) {
            return null;
        }
        Type type = localBadges().get(key);
        return type != null ? type : remote.get(key);
    }

    public static boolean hasAnyBadges() {
        return !localBadges().isEmpty() || !remote.isEmpty();
    }

    // endregion

    // region remote list

    public static void refreshRemote(boolean force) {
        if (!Settings.PRISM_REMOTE_BADGES.get()) {
            remote = Collections.emptyMap();
            return;
        }
        if (remote.isEmpty()) {
            remote = Collections.unmodifiableMap(parse(Settings.PRISM_REMOTE_BADGE_CACHE.get()));
        }
        long last = Settings.PRISM_REMOTE_BADGE_FETCHED.get();
        if (!force && (remoteRequested || System.currentTimeMillis() - last < REMOTE_REFRESH_MS)) {
            return;
        }
        remoteRequested = true;
        Thread thread = new Thread(PrismBadges::fetchRemote, "Prism-badges");
        thread.setDaemon(true);
        thread.start();
    }

    private static void fetchRemote() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(REMOTE_URL).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            int code = connection.getResponseCode();
            if (code == 404) {
                // No file published yet: nothing to show.
                Settings.PRISM_REMOTE_BADGE_FETCHED.save(System.currentTimeMillis());
                return;
            }
            if (code != 200) {
                return;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && body.length() < 64 * 1024) {
                    body.append(line).append('\n');
                }
            }
            String text = body.toString();
            remote = Collections.unmodifiableMap(parse(text));
            Settings.PRISM_REMOTE_BADGE_CACHE.save(serialize(remote));
            Settings.PRISM_REMOTE_BADGE_FETCHED.save(System.currentTimeMillis());
            PrismUi.badgesChanged();
        } catch (Throwable throwable) {
            Logger.printDebug(() -> "Prism: badge list fetch failed: " + throwable);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // endregion

    // region text decoration

    /** Appends the badge to a nickname TextView. Returns true if the text was changed. */
    public static boolean decorate(TextView view) {
        CharSequence text = view.getText();
        if (text == null || text.length() == 0) {
            return false;
        }
        if (text instanceof Spanned && ((Spanned) text).getSpans(0, text.length(), BadgeSpan.class).length > 0) {
            return false;
        }
        Type type = lookup(text);
        if (type == null) {
            return false;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int start = builder.length();
        builder.append('⁠');
        builder.setSpan(new BadgeSpan(type), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(builder);
        return true;
    }

    /** Draws the badge after the nickname, vertically centered on the text. */
    public static final class BadgeSpan extends ReplacementSpan {
        private final Type type;

        BadgeSpan(Type type) {
            this.type = type;
        }

        private static int size(Paint paint) {
            return Math.round(paint.getTextSize() * 1.08f);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            int size = size(paint);
            return size + Math.round(paint.getTextSize() * 0.28f);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, @NonNull Paint paint) {
            int size = size(paint);
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            float centerY = y + (metrics.ascent + metrics.descent) / 2f;
            float left = x + paint.getTextSize() * 0.24f;
            BadgeDrawable drawable = new BadgeDrawable(type);
            drawable.setBounds(Math.round(left), Math.round(centerY - size / 2f),
                    Math.round(left + size), Math.round(centerY + size / 2f));
            drawable.draw(canvas);
        }
    }

    /**
     * Rosette seal filled with the gradient of the selected app icon and a white glyph,
     * like a verified mark.
     */
    public static final class BadgeDrawable extends Drawable {
        private final Type type;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public BadgeDrawable(Type type) {
            this.type = type;
            glyph.setColor(Color.WHITE);
            glyph.setStrokeCap(Paint.Cap.ROUND);
            glyph.setStrokeJoin(Paint.Join.ROUND);
            rim.setStyle(Paint.Style.STROKE);
            rim.setColor(Color.argb(150, 255, 255, 255));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            float radius = Math.min(bounds.width(), bounds.height()) / 2f;
            int[] colors = PrismIcons.current().badgeColors();

            // Soft glow behind the seal.
            glow.setColor(PrismTheme.withAlpha(colors[0], 0.35f));
            canvas.drawCircle(cx, cy, radius * 0.98f, glow);

            fill.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    colors[0], colors[1], Shader.TileMode.CLAMP));
            rosette(cx, cy, radius * 0.92f, radius * 0.78f, 12);
            canvas.drawPath(path, fill);
            rim.setStrokeWidth(Math.max(1f, radius * 0.07f));
            canvas.drawPath(path, rim);

            // Glass shine on the upper half.
            Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
            shine.setShader(new LinearGradient(cx, bounds.top, cx, cy,
                    Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy - radius * 0.12f, radius * 0.6f, shine);

            drawGlyph(canvas, cx, cy, radius * 0.5f);
        }

        private void rosette(float cx, float cy, float outer, float inner, int points) {
            path.reset();
            int steps = points * 2;
            for (int i = 0; i <= steps; i++) {
                double angle = Math.PI * i / points - Math.PI / 2;
                float r = (i % 2 == 0) ? outer : inner;
                float px = (float) (cx + Math.cos(angle) * r);
                float py = (float) (cy + Math.sin(angle) * r);
                if (i == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            path.close();
        }

        private void drawGlyph(Canvas canvas, float cx, float cy, float r) {
            Path g = new Path();
            switch (type) {
                case VERIFIED:
                    glyph.setStyle(Paint.Style.STROKE);
                    glyph.setStrokeWidth(r * 0.32f);
                    g.moveTo(cx - r * 0.62f, cy + r * 0.02f);
                    g.lineTo(cx - r * 0.16f, cy + r * 0.48f);
                    g.lineTo(cx + r * 0.66f, cy - r * 0.42f);
                    canvas.drawPath(g, glyph);
                    break;
                case CROWN:
                    glyph.setStyle(Paint.Style.FILL);
                    g.moveTo(cx - r * 0.78f, cy + r * 0.5f);
                    g.lineTo(cx - r * 0.86f, cy - r * 0.42f);
                    g.lineTo(cx - r * 0.36f, cy + r * 0.02f);
                    g.lineTo(cx, cy - r * 0.62f);
                    g.lineTo(cx + r * 0.36f, cy + r * 0.02f);
                    g.lineTo(cx + r * 0.86f, cy - r * 0.42f);
                    g.lineTo(cx + r * 0.78f, cy + r * 0.5f);
                    g.close();
                    canvas.drawPath(g, glyph);
                    break;
                case STAR:
                    glyph.setStyle(Paint.Style.FILL);
                    for (int i = 0; i <= 10; i++) {
                        double angle = Math.PI * i / 5 - Math.PI / 2;
                        float rr = (i % 2 == 0) ? r * 0.92f : r * 0.4f;
                        float px = (float) (cx + Math.cos(angle) * rr);
                        float py = (float) (cy + Math.sin(angle) * rr + r * 0.06f);
                        if (i == 0) {
                            g.moveTo(px, py);
                        } else {
                            g.lineTo(px, py);
                        }
                    }
                    g.close();
                    canvas.drawPath(g, glyph);
                    break;
                case HEART:
                    glyph.setStyle(Paint.Style.FILL);
                    g.moveTo(cx, cy + r * 0.72f);
                    g.cubicTo(cx - r * 1.15f, cy - r * 0.05f, cx - r * 0.55f, cy - r * 0.98f, cx, cy - r * 0.36f);
                    g.cubicTo(cx + r * 0.55f, cy - r * 0.98f, cx + r * 1.15f, cy - r * 0.05f, cx, cy + r * 0.72f);
                    g.close();
                    canvas.drawPath(g, glyph);
                    break;
                case BOLT:
                    glyph.setStyle(Paint.Style.FILL);
                    g.moveTo(cx + r * 0.18f, cy - r * 0.9f);
                    g.lineTo(cx - r * 0.56f, cy + r * 0.12f);
                    g.lineTo(cx - r * 0.02f, cy + r * 0.12f);
                    g.lineTo(cx - r * 0.2f, cy + r * 0.9f);
                    g.lineTo(cx + r * 0.58f, cy - r * 0.16f);
                    g.lineTo(cx + r * 0.04f, cy - r * 0.16f);
                    g.close();
                    canvas.drawPath(g, glyph);
                    break;
                case COMET:
                default:
                    glyph.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx + r * 0.22f, cy + r * 0.22f, r * 0.42f, glyph);
                    glyph.setStyle(Paint.Style.STROKE);
                    glyph.setStrokeWidth(r * 0.2f);
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.75f, cx - r * 0.05f, cy - r * 0.05f, glyph);
                    canvas.drawLine(cx - r * 0.2f, cy - r * 0.85f, cx + r * 0.2f, cy - r * 0.45f, glyph);
                    canvas.drawLine(cx - r * 0.85f, cy - r * 0.2f, cx - r * 0.45f, cy + r * 0.2f, glyph);
                    break;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            glyph.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 64;
        }

        @Override
        public int getIntrinsicHeight() {
            return 64;
        }
    }

    // endregion

    static Map<String, Type> merged() {
        Map<String, Type> all = new HashMap<>(remote);
        all.putAll(localBadges());
        return all;
    }
}
