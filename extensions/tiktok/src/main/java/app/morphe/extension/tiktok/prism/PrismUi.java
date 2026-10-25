package app.morphe.extension.tiktok.prism;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Runtime UI layer of Prism: liquid glass bottom bar and nickname badges.
 * Works on views only (no obfuscated TikTok classes), so it survives app updates
 * and silently does nothing when it cannot find what it looks for.
 */
@SuppressWarnings("unused")
public final class PrismUi {
    private static final String MAIN_ACTIVITY = "com.ss.android.ugc.aweme.main.MainActivity";
    private static final int SCAN_DELAY_MS = 220;
    private static final int TAG_SCANNER = 0x4D455431;

    private static boolean registered;
    private static final Map<Activity, Scanner> scanners = new WeakHashMap<>();

    private PrismUi() {
    }

    /** Called from MainActivity.onCreate by the "Prism UI" patch. */
    public static void onMainActivityCreate(Activity activity) {
        try {
            // Runs before super.onCreate(): only register here. Touching the window now would
            // create the decor view too early; onActivityResumed attaches instead.
            if (!registered) {
                registered = true;
                activity.getApplication().registerActivityLifecycleCallbacks(new Callbacks());
                PrismBadges.refreshRemote(false);
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "Prism UI init failed", throwable);
        }
    }

    /** Re-run decoration everywhere, e.g. after a badge was granted. */
    public static void badgesChanged() {
        for (Scanner scanner : new ArrayList<>(scanners.values())) {
            scanner.schedule();
        }
    }

    /** Re-apply glass after the theme or the toggle changed. */
    public static void styleChanged() {
        GlassBarDrawable.version++;
        badgesChanged();
    }

    private static void attach(Activity activity) {
        if (scanners.containsKey(activity)) {
            scanners.get(activity).schedule();
            return;
        }
        Scanner scanner = new Scanner(activity);
        scanners.put(activity, scanner);
        scanner.install();
    }

    private static final class Callbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            try {
                attach(activity);
            } catch (Throwable throwable) {
                Logger.printDebug(() -> "Prism attach failed: " + throwable);
            }
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            scanners.remove(activity);
        }
    }

    private static final class Scanner implements ViewTreeObserver.OnGlobalLayoutListener, Runnable {
        private final WeakReference<Activity> activityRef;
        private final boolean isMain;
        private WeakReference<View> barRef = new WeakReference<>(null);
        private View decor;

        Scanner(Activity activity) {
            activityRef = new WeakReference<>(activity);
            isMain = MAIN_ACTIVITY.equals(activity.getClass().getName());
        }

        void install() {
            Activity activity = activityRef.get();
            if (activity == null || activity.getWindow() == null) {
                return;
            }
            decor = activity.getWindow().getDecorView();
            decor.getViewTreeObserver().addOnGlobalLayoutListener(this);
            schedule();
        }

        void schedule() {
            if (decor == null) {
                return;
            }
            decor.removeCallbacks(this);
            decor.postDelayed(this, SCAN_DELAY_MS);
        }

        @Override
        public void onGlobalLayout() {
            schedule();
        }

        @Override
        public void run() {
            try {
                View root = decor;
                if (root == null || !root.isAttachedToWindow()) {
                    return;
                }
                if (isMain) {
                    updateGlassBar(root);
                }
                if (PrismBadges.enabled() && PrismBadges.hasAnyBadges()) {
                    decorateBadges(root);
                }
            } catch (Throwable throwable) {
                Logger.printDebug(() -> "Prism scan failed: " + throwable);
            }
        }

        // region badges

        private void decorateBadges(View view) {
            if (view.getVisibility() != View.VISIBLE) {
                return;
            }
            if (view instanceof TextView) {
                if (!(view instanceof EditText)) {
                    PrismBadges.decorate((TextView) view);
                }
                return;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0, count = group.getChildCount(); i < count; i++) {
                    decorateBadges(group.getChildAt(i));
                }
            }
        }

        // endregion

        // region glass bar

        private void updateGlassBar(View root) {
            View bar = barRef.get();
            if (bar == null || !bar.isAttachedToWindow() || !looksLikeBottomBar(root, bar, true)) {
                bar = findBottomBar(root);
                barRef = new WeakReference<>(bar);
                if (bar == null) {
                    return;
                }
                bar.getViewTreeObserver().addOnPreDrawListener(new GlassKeeper(bar));
            }
            GlassKeeper.apply(bar);
        }

        @Nullable
        private View findBottomBar(View root) {
            List<View> candidates = new ArrayList<>();
            collectTabRows(root, root, candidates);
            if (candidates.isEmpty()) {
                return null;
            }
            // The lowest row on screen is the navigation bar.
            View best = null;
            int bestBottom = Integer.MIN_VALUE;
            int[] location = new int[2];
            for (View candidate : candidates) {
                candidate.getLocationOnScreen(location);
                int bottom = location[1] + candidate.getHeight();
                if (bottom > bestBottom) {
                    bestBottom = bottom;
                    best = candidate;
                }
            }
            if (best == null) {
                return null;
            }
            // Climb to the outermost parent with the same bounds: that one carries the background.
            View bar = best;
            while (bar.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) bar.getParent();
                if (parent == root || Math.abs(parent.getHeight() - bar.getHeight()) > 2
                        || Math.abs(parent.getWidth() - bar.getWidth()) > 2) {
                    break;
                }
                bar = parent;
            }
            // Inner layers between the tabs and the bar must not paint over the glass.
            View inner = best;
            while (inner != bar && inner != null) {
                if (inner.getBackground() instanceof ColorDrawable) {
                    inner.setBackground(null);
                }
                inner = inner.getParent() instanceof View ? (View) inner.getParent() : null;
            }
            return bar;
        }

        private void collectTabRows(View root, View view, List<View> out) {
            if (view.getVisibility() != View.VISIBLE || !(view instanceof ViewGroup)) {
                return;
            }
            ViewGroup group = (ViewGroup) view;
            if (looksLikeBottomBar(root, group, false)) {
                out.add(group);
            }
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                collectTabRows(root, group.getChildAt(i), out);
            }
        }

        private static boolean looksLikeBottomBar(View root, View view, boolean alreadyKnown) {
            Context context = view.getContext();
            float density = context.getResources().getDisplayMetrics().density;
            int width = view.getWidth();
            int height = view.getHeight();
            if (width < root.getWidth() * 0.9f || height < 40 * density || height > 110 * density) {
                return false;
            }
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int[] rootLocation = new int[2];
            root.getLocationOnScreen(rootLocation);
            int distanceToBottom = (rootLocation[1] + root.getHeight()) - (location[1] + height);
            if (distanceToBottom < -2 || distanceToBottom > 72 * density) {
                return false;
            }
            if (alreadyKnown) {
                return view.isShown();
            }
            if (!(view instanceof ViewGroup)) {
                return false;
            }
            ViewGroup group = (ViewGroup) view;
            int visible = 0;
            int minChild = Integer.MAX_VALUE;
            int maxChild = 0;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || child.getWidth() == 0) {
                    continue;
                }
                visible++;
                minChild = Math.min(minChild, child.getWidth());
                maxChild = Math.max(maxChild, child.getWidth());
            }
            // Four to six tabs of roughly equal width side by side.
            return visible >= 4 && visible <= 6 && maxChild <= minChild * 2.2f
                    && maxChild <= width / 2;
        }

        // endregion
    }

    /** Puts the glass back whenever TikTok repaints the bar with its own color. */
    private static final class GlassKeeper implements ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<View> barRef;

        GlassKeeper(View bar) {
            barRef = new WeakReference<>(bar);
        }

        @Override
        public boolean onPreDraw() {
            View bar = barRef.get();
            if (bar == null) {
                return true;
            }
            if (!bar.isAttachedToWindow()) {
                ViewTreeObserver observer = bar.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }
                return true;
            }
            apply(bar);
            return true;
        }

        static void apply(View bar) {
            Drawable background = bar.getBackground();
            boolean enabled = Settings.PRISM_GLASS_NAV.get();
            if (background instanceof GlassBarDrawable) {
                GlassBarDrawable glass = (GlassBarDrawable) background;
                if (!enabled) {
                    bar.setBackground(new ColorDrawable(glass.baseColor));
                } else if (glass.builtFor != GlassBarDrawable.version) {
                    bar.setBackground(new GlassBarDrawable(bar.getContext(), glass.baseColor));
                }
                return;
            }
            if (!enabled) {
                return;
            }
            int base = Color.BLACK;
            if (background instanceof ColorDrawable) {
                base = ((ColorDrawable) background).getColor();
            } else if (background == null) {
                Object previous = bar.getTag(TAG_SCANNER);
                if (previous instanceof Integer) {
                    base = (Integer) previous;
                }
            }
            bar.setTag(TAG_SCANNER, base);
            bar.setBackground(new GlassBarDrawable(bar.getContext(), base));
        }
    }

    /**
     * Liquid glass: translucent tint of TikTok's own bar color (so icon contrast is kept),
     * a sheen on the upper half, a glowing theme rim and rounded top corners.
     */
    static final class GlassBarDrawable extends Drawable {
        static int version;

        final int baseColor;
        final int builtFor;
        private final float radius;
        private final float rimWidth;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        GlassBarDrawable(Context context, int baseColor) {
            this.baseColor = baseColor;
            this.builtFor = version;
            float density = context.getResources().getDisplayMetrics().density;
            radius = 22 * density;
            rimWidth = Math.max(1f, 1.2f * density);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(rimWidth);
        }

        private static boolean isLight(int color) {
            double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
            return luminance > 0.6;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            PrismTheme theme = PrismTheme.current();
            rect.set(getBounds());
            if (rect.isEmpty()) {
                return;
            }
            float baseAlpha = Color.alpha(baseColor) / 255f;
            boolean light = isLight(baseColor);
            int opaqueBase = Color.rgb(Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            int tinted = PrismTheme.blend(opaqueBase, theme.accent, light ? 0.05f : 0.10f);

            path.reset();
            path.addRoundRect(rect, new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);

            // Base frosted layer.
            fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    PrismTheme.withAlpha(tinted, Math.min(baseAlpha, light ? 0.80f : 0.74f)),
                    PrismTheme.withAlpha(tinted, Math.min(baseAlpha, light ? 0.92f : 0.90f)),
                    Shader.TileMode.CLAMP));
            canvas.drawPath(path, fill);

            // Sheen: light caught on the top edge of the glass.
            sheen.setShader(new LinearGradient(0, rect.top, 0, rect.top + rect.height() * 0.6f,
                    Color.argb(light ? 120 : 46, 255, 255, 255), Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP));
            canvas.drawPath(path, sheen);

            // Glowing rim in the theme colors, fading out at the sides.
            float inset = rimWidth / 2f;
            rect.inset(inset, inset);
            path.reset();
            path.addRoundRect(rect, new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
            rim.setShader(new LinearGradient(rect.left, 0, rect.right, 0,
                    new int[]{
                            PrismTheme.withAlpha(theme.accent, 0.0f),
                            PrismTheme.withAlpha(theme.accent, 0.85f),
                            PrismTheme.withAlpha(Color.WHITE, light ? 0.9f : 0.55f),
                            PrismTheme.withAlpha(theme.accent2, 0.85f),
                            PrismTheme.withAlpha(theme.accent2, 0.0f)
                    },
                    new float[]{0f, 0.2f, 0.5f, 0.8f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.save();
            canvas.clipRect(rect.left - inset, rect.top - inset, rect.right + inset, rect.top + radius * 1.2f);
            canvas.drawPath(path, rim);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
