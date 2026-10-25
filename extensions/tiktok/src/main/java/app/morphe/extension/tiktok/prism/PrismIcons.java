package app.morphe.extension.tiktok.prism;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Launcher icon switcher. The "App icons" patch adds one disabled launcher activity-alias per
 * icon; switching enables the chosen alias and disables the rest.
 */
@SuppressWarnings("unused")
public enum PrismIcons {
    CLASSIC("classic", "Классика", null, Color.rgb(37, 244, 238), Color.rgb(254, 44, 85)),
    PRISM("prism", "Prism", "prism_icon_prism", Color.rgb(139, 92, 246), Color.rgb(34, 211, 238)),
    HALLOWEEN("halloween", "Хеллоуин", "prism_icon_halloween", Color.rgb(255, 140, 26), Color.rgb(142, 45, 226)),
    FEMBOY("femboy", "Фембой", "prism_icon_femboy", Color.rgb(255, 120, 190), Color.rgb(126, 200, 255)),
    NEON("neon", "Неон", "prism_icon_neon", Color.rgb(57, 255, 20), Color.rgb(255, 0, 229)),
    OCEAN("ocean", "Океан", "prism_icon_ocean", Color.rgb(0, 194, 255), Color.rgb(0, 214, 143)),
    GOLD("gold", "Золото", "prism_icon_gold", Color.rgb(255, 214, 64), Color.rgb(230, 120, 20)),
    SAKURA("sakura", "Сакура", "prism_icon_sakura", Color.rgb(244, 114, 182), Color.rgb(168, 85, 247));

    /** Alias name prefix; must match the "App icons" patch. */
    public static final String ALIAS_PREFIX = "com.ss.android.ugc.aweme.splash.PrismIcon_";

    public final String key;
    public final String title;
    /** Mipmap added by the patch, or null for the stock TikTok icon. */
    @Nullable
    private final String resourceName;
    private final int badgeStart;
    private final int badgeEnd;

    PrismIcons(String key, String title, @Nullable String resourceName, int badgeStart, int badgeEnd) {
        this.key = key;
        this.title = title;
        this.resourceName = resourceName;
        this.badgeStart = badgeStart;
        this.badgeEnd = badgeEnd;
    }

    public static PrismIcons current() {
        String key;
        try {
            key = Settings.PRISM_APP_ICON.get();
        } catch (Throwable ignored) {
            key = CLASSIC.key;
        }
        return fromKey(key);
    }

    public static PrismIcons fromKey(String key) {
        for (PrismIcons icon : values()) {
            if (icon.key.equals(key)) {
                return icon;
            }
        }
        return CLASSIC;
    }

    /** Badge gradient, so nickname badges match the launcher icon. */
    public int[] badgeColors() {
        return new int[]{badgeStart, badgeEnd};
    }

    public String aliasName() {
        return ALIAS_PREFIX + key;
    }

    /** True when the APK was patched with the "App icons" patch. */
    public static boolean available(Context context) {
        try {
            context.getPackageManager().getActivityInfo(
                    new ComponentName(context.getPackageName(), CLASSIC.aliasName()),
                    0x00000200 // MATCH_DISABLED_COMPONENTS
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public Drawable preview(Context context) {
        try {
            int id;
            if (resourceName == null) {
                id = context.getApplicationInfo().icon;
            } else {
                id = context.getResources().getIdentifier(resourceName, "mipmap", context.getPackageName());
            }
            if (id == 0) {
                return null;
            }
            return context.getResources().getDrawable(id, context.getTheme());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Enables this icon's alias and disables all others. */
    public boolean apply(Context context) {
        PackageManager manager = context.getPackageManager();
        String packageName = context.getPackageName();
        try {
            // Enable the new one first so the app never has zero launcher entries.
            manager.setComponentEnabledSetting(
                    new ComponentName(packageName, aliasName()),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );
            for (PrismIcons other : values()) {
                if (other == this) {
                    continue;
                }
                manager.setComponentEnabledSetting(
                        new ComponentName(packageName, other.aliasName()),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                );
            }
            Settings.PRISM_APP_ICON.save(key);
            return true;
        } catch (Throwable throwable) {
            Logger.printException(() -> "Prism: could not switch the app icon", throwable);
            return false;
        }
    }
}
