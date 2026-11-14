package app.morphe.extension.tiktok.prism;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;

import app.morphe.extension.shared.Utils;

/**
 * Recolors TikTok's brand colors with the selected Prism theme across the whole app.
 * The "Prism theme colors" patch routes TikTok's color lookups through these bridges.
 * Colors drawn directly from XML drawables or hardcoded in Compose are not covered.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class PrismColors {
    // TikTok red/pink and cyan as used across 47.x.
    private static final int[] BRAND_RED = {0xFE2C55, 0xFF3B5C, 0xEA4359, 0xF7375A, 0xE62C4F, 0xF93659};
    private static final int[] BRAND_CYAN = {0x25F4EE, 0x20D5EC, 0x00F2EA};

    // Surface greys of TikTok's dark and light screens (never used for text there).
    private static final int[] DARK_SURFACES = {0x121212, 0x1E1E1E, 0x252525};
    private static final int[] LIGHT_SURFACES = {0xF8F8F8, 0xF5F5F5};

    private static volatile boolean ready;

    private PrismColors() {
    }

    public static int map(int color) {
        try {
            if (!ready) {
                // Settings must not be touched before the extension context exists,
                // or its static initializer fails for the rest of the process.
                if (Utils.getContext() == null) {
                    return color;
                }
                ready = true;
            }
            PrismTheme theme = PrismTheme.current();
            if (theme == PrismTheme.CLASSIC) {
                return color;
            }
            int rgb = color & 0xFFFFFF;
            int alpha = color & 0xFF000000;
            for (int red : BRAND_RED) {
                if (rgb == red) {
                    return alpha | (theme.accent & 0xFFFFFF);
                }
            }
            for (int cyan : BRAND_CYAN) {
                if (rgb == cyan) {
                    return alpha | (theme.accent2 & 0xFFFFFF);
                }
            }
            // Surfaces pick up the theme's background tint.
            for (int surface : DARK_SURFACES) {
                if (rgb == surface) {
                    int target = theme.isDark() ? theme.backgroundTop()
                            : PrismTheme.blend(0xFF000000, theme.accent, 0.25f);
                    return alpha | (PrismTheme.blend(0xFF000000 | rgb, target, 0.65f) & 0xFFFFFF);
                }
            }
            for (int surface : LIGHT_SURFACES) {
                if (rgb == surface) {
                    int target = theme.isDark() ? PrismTheme.blend(0xFFFFFFFF, theme.accent, 0.12f)
                            : theme.backgroundTop();
                    return alpha | (PrismTheme.blend(0xFF000000 | rgb, target, 0.65f) & 0xFFFFFF);
                }
            }
        } catch (Throwable ignored) {
            // Never break TikTok's drawing over a color.
        }
        return color;
    }

    private static ColorStateList map(ColorStateList list) {
        if (list == null || list.isStateful()) {
            return list;
        }
        int original = list.getDefaultColor();
        int mapped = map(original);
        return mapped == original ? list : ColorStateList.valueOf(mapped);
    }

    // region bridges, the receiver becomes the first argument

    public static int getColor(Context context, int id) {
        return map(context.getColor(id));
    }

    public static int getColor(Resources resources, int id) {
        return map(resources.getColor(id));
    }

    public static int getColor(Resources resources, int id, Resources.Theme theme) {
        return map(resources.getColor(id, theme));
    }

    public static int getColor(TypedArray array, int index, int defValue) {
        return map(array.getColor(index, defValue));
    }

    public static ColorStateList getColorStateList(Resources resources, int id) {
        return map(resources.getColorStateList(id));
    }

    public static ColorStateList getColorStateList(Resources resources, int id, Resources.Theme theme) {
        return map(resources.getColorStateList(id, theme));
    }

    public static ColorStateList getColorStateList(TypedArray array, int index) {
        return map(array.getColorStateList(index));
    }

    // endregion
}
