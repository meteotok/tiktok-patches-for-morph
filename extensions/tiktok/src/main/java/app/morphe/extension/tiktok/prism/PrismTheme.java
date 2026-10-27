package app.morphe.extension.tiktok.prism;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;

import androidx.annotation.ColorInt;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Prism color themes. A theme reskins the mod settings, the liquid glass
 * bottom bar highlight and the dialogs of the mod.
 */
@SuppressWarnings("unused")
public enum PrismTheme {
    CLASSIC("classic", "Классика", "Цвета TikTok, светлая или тёмная по системе",
            0, rgb(254, 44, 85), rgb(37, 244, 238), 0, 0, 0),
    PRISM("prism", "Prism", "Ночное небо, стекло и радужные блики",
            1, rgb(139, 92, 246), rgb(34, 211, 238), rgb(14, 12, 34), rgb(5, 6, 18), rgb(240, 240, 255)),
    HALLOWEEN("halloween", "Хеллоуин", "Тыквы, ведьмы и фиолетовый туман",
            1, rgb(255, 122, 24), rgb(142, 45, 226), rgb(28, 12, 38), rgb(10, 5, 14), rgb(255, 236, 214)),
    FEMBOY("femboy", "Фембой", "Пастельный розовый и нежно-голубой",
            2, rgb(255, 111, 181), rgb(126, 200, 255), rgb(255, 240, 248), rgb(234, 244, 255), rgb(74, 38, 72)),
    NEON("neon", "Неон", "Киберпанк, кислотный зелёный и маджента",
            1, rgb(57, 255, 20), rgb(255, 0, 229), rgb(6, 10, 8), rgb(0, 0, 0), rgb(230, 255, 230)),
    OCEAN("ocean", "Океан", "Глубокий синий и бирюза",
            1, rgb(0, 194, 255), rgb(0, 255, 163), rgb(4, 24, 42), rgb(2, 9, 18), rgb(226, 246, 255)),
    GOLD("gold", "Золото", "Чёрный и золото, дорого-богато",
            1, rgb(255, 196, 0), rgb(255, 120, 40), rgb(22, 18, 10), rgb(6, 5, 3), rgb(255, 246, 220)),
    SAKURA("sakura", "Сакура", "Светлая весна, розовые лепестки",
            2, rgb(236, 72, 153), rgb(168, 85, 247), rgb(255, 246, 249), rgb(253, 236, 244), rgb(80, 28, 52));

    public final String key;
    public final String title;
    public final String description;
    /** 0 = follow system, 1 = dark, 2 = light. */
    private final int mode;
    public final @ColorInt int accent;
    public final @ColorInt int accent2;
    private final @ColorInt int backgroundTop;
    private final @ColorInt int backgroundBottom;
    private final @ColorInt int text;

    private static PrismTheme cached;
    private static String cachedKey;

    PrismTheme(String key, String title, String description, int mode,
               int accent, int accent2, int backgroundTop, int backgroundBottom, int text) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.mode = mode;
        this.accent = accent;
        this.accent2 = accent2;
        this.backgroundTop = backgroundTop;
        this.backgroundBottom = backgroundBottom;
        this.text = text;
    }

    private static int rgb(int r, int g, int b) {
        return Color.rgb(r, g, b);
    }

    public static PrismTheme current() {
        String key;
        try {
            key = Settings.PRISM_THEME.get();
        } catch (Throwable ignored) {
            key = CLASSIC.key;
        }
        if (cached != null && key.equals(cachedKey)) {
            return cached;
        }
        cached = fromKey(key);
        cachedKey = key;
        return cached;
    }

    public static PrismTheme fromKey(String key) {
        for (PrismTheme theme : values()) {
            if (theme.key.equals(key)) {
                return theme;
            }
        }
        return CLASSIC;
    }

    public boolean followsSystem() {
        return mode == 0;
    }

    public boolean isDark() {
        if (mode == 1) {
            return true;
        }
        if (mode == 2) {
            return false;
        }
        Configuration config = Resources.getSystem().getConfiguration();
        return (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public @ColorInt int backgroundTop() {
        if (mode == 0) {
            return isDark() ? rgb(16, 14, 20) : Color.WHITE;
        }
        return backgroundTop;
    }

    public @ColorInt int backgroundBottom() {
        if (mode == 0) {
            return isDark() ? rgb(8, 8, 10) : rgb(246, 246, 249);
        }
        return backgroundBottom;
    }

    public @ColorInt int textPrimary() {
        if (mode == 0) {
            return isDark() ? rgb(242, 242, 245) : Color.BLACK;
        }
        return text;
    }

    public @ColorInt int textSecondary() {
        return withAlpha(textPrimary(), isDark() ? 0.62f : 0.66f);
    }

    public @ColorInt int textDisabled() {
        return withAlpha(textPrimary(), 0.42f);
    }

    /** Fill of a liquid glass card on top of the theme background. */
    public @ColorInt int glassFill() {
        return isDark() ? Color.argb(26, 255, 255, 255) : Color.argb(150, 255, 255, 255);
    }

    /** Outline of a liquid glass card. */
    public @ColorInt int glassStroke() {
        return isDark() ? Color.argb(46, 255, 255, 255) : Color.argb(200, 255, 255, 255);
    }

    public @ColorInt int divider() {
        return isDark() ? Color.argb(20, 255, 255, 255) : Color.argb(22, 0, 0, 0);
    }

    public static @ColorInt int withAlpha(@ColorInt int color, float alpha) {
        return Color.argb(Math.round(Color.alpha(color) * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static @ColorInt int blend(@ColorInt int from, @ColorInt int to, float ratio) {
        float inverse = 1f - ratio;
        return Color.argb(
                Math.round(Color.alpha(from) * inverse + Color.alpha(to) * ratio),
                Math.round(Color.red(from) * inverse + Color.red(to) * ratio),
                Math.round(Color.green(from) * inverse + Color.green(to) * ratio),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * ratio)
        );
    }
}
