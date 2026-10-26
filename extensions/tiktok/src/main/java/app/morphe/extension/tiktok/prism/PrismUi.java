package app.morphe.extension.tiktok.prism;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
            try {
                if (savedInstanceState == null) {
                    app.morphe.extension.tiktok.feedfilter.LinkOpenExemption.onIntent(activity.getIntent());
                }
            } catch (Throwable ignored) {
                // Only an exemption hint.
            }
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            try {
                attach(activity);
                if (MAIN_ACTIVITY.equals(activity.getClass().getName())) {
                    PrismUpdate.checkOnce(activity);
                }
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

    private static final class Scanner implements ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnScrollChangedListener, Runnable {
        private final WeakReference<Activity> activityRef;
        private final boolean isMain;
        private WeakReference<View> barRef = new WeakReference<>(null);
        private WeakReference<PrismDecor> decorRef = new WeakReference<>(null);
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
            // Scrolling moves content under the decorations without a layout pass.
            decor.getViewTreeObserver().addOnScrollChangedListener(this);
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
        public void onScrollChanged() {
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
                updateDecor(root);
                // The name hook adds the badge emoji everywhere (Compose included); where the
                // name is a TextView, the emoji is swapped for the sticker image.
                if (PrismBadges.enabled() && PrismBadges.hasAnyBadges()) {
                    decorateBadges(root);
                }
            } catch (Throwable throwable) {
                Logger.printDebug(() -> "Prism scan failed: " + throwable);
            }
        }

        // region theme decorations

        private void updateDecor(View root) {
            if (!PrismDecor.enabled()) {
                PrismDecor existing = decorRef.get();
                if (existing != null) {
                    existing.invalidateSelf();
                }
                return;
            }
            PrismDecor layer = PrismDecor.attach(root);
            decorRef = new WeakReference<>(layer);
            int top = 0;
            int bottom = 0;
            android.view.WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            View bar = barRef.get();
            if (bar != null && bar.isShown()) {
                int[] barLocation = new int[2];
                int[] rootLocation = new int[2];
                bar.getLocationOnScreen(barLocation);
                root.getLocationOnScreen(rootLocation);
                bottom = Math.max(bottom, rootLocation[1] + root.getHeight() - barLocation[1]);
            }
            layer.setInsets(top, bottom);
            layer.setVariant(screenVariant(root));
            collectObstacles(root);
            layer.invalidateSelf();
        }

        private final List<RectF> blocked = new ArrayList<>();
        private final android.graphics.Rect visible = new android.graphics.Rect();

        /**
         * Finds where TikTok has something to read or tap: text, inputs, icons and buttons.
         * A corner with any of these hides its decoration. Videos, covers and thumbnails are
         * fine to sit on the corner of.
         */
        private void collectObstacles(View root) {
            blocked.clear();
            float screen = (float) root.getWidth() * root.getHeight();
            if (screen > 0) {
                collectObstacles(root, screen);
            }
            PrismDecor layer = decorRef.get();
            if (layer != null) {
                layer.setObstacles(toArray(blocked));
            }
        }

        private void collectObstacles(View view, float screen) {
            if (blocked.size() > 600 || view.getVisibility() != View.VISIBLE || view.getAlpha() < 0.05f
                    || !view.getGlobalVisibleRect(visible)) {
                return;
            }
            float density = view.getResources().getDisplayMetrics().density;
            boolean small = visible.width() < 120 * density && visible.height() < 120 * density;
            String name = view.getClass().getName();
            if (view instanceof android.view.TextureView || view instanceof android.view.SurfaceView) {
                return;
            }
            if (name.contains("ComposeView") || name.contains("WebView")) {
                // Nothing inside can be seen from here, so the whole thing is off limits.
                blocked.add(new RectF(visible));
                return;
            }
            if (view instanceof EditText) {
                // Inputs get extra room so the keyboard row and send button stay clear.
                RectF rect = new RectF(visible);
                float pad = 18 * density;
                rect.inset(-pad, -pad);
                blocked.add(rect);
                return;
            }
            if (view instanceof TextView) {
                if (((TextView) view).length() > 0) {
                    blocked.add(new RectF(visible));
                }
                return;
            }
            if (view instanceof android.widget.ImageView) {
                // Icons and avatars; big images are covers and thumbnails.
                if (small) {
                    blocked.add(new RectF(visible));
                }
                return;
            }
            if ((view.isClickable() || view.isLongClickable()) && small) {
                blocked.add(new RectF(visible));
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0, count = group.getChildCount(); i < count; i++) {
                    collectObstacles(group.getChildAt(i), screen);
                }
            }
        }

        private static float[] toArray(List<RectF> rects) {
            float[] out = new float[rects.size() * 4];
            for (int i = 0; i < rects.size(); i++) {
                RectF rect = rects.get(i);
                out[i * 4] = rect.left;
                out[i * 4 + 1] = rect.top;
                out[i * 4 + 2] = rect.right;
                out[i * 4 + 3] = rect.bottom;
            }
            return out;
        }

        /** Bottom tab index on the main screen, else a stable number per screen class. */
        private int screenVariant(View root) {
            View bar = barRef.get();
            if (isMain && bar != null && bar.isShown()) {
                View selected = selectedTab(bar);
                if (selected != null && selected.getParent() instanceof ViewGroup) {
                    return ((ViewGroup) selected.getParent()).indexOfChild(selected);
                }
            }
            Activity activity = activityRef.get();
            return activity == null ? 0 : (activity.getClass().getName().hashCode() & 0x7fffffff) % 4;
        }

        // endregion

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
            View selected = selectedTab(bar);
            if (selected != lastSelected.get()) {
                lastSelected = new WeakReference<>(selected);
                bar.invalidate();
            }
            return true;
        }

        private WeakReference<View> lastSelected = new WeakReference<>(null);

        static void apply(View bar) {
            Drawable background = bar.getBackground();
            boolean enabled = Settings.PRISM_GLASS_NAV.get();
            if (background instanceof GlassBarDrawable) {
                GlassBarDrawable glass = (GlassBarDrawable) background;
                if (!enabled) {
                    bar.setBackground(new ColorDrawable(glass.baseColor));
                } else if (glass.builtFor != GlassBarDrawable.version) {
                    bar.setBackground(new GlassBarDrawable(bar, glass.baseColor));
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
            bar.setBackground(new GlassBarDrawable(bar, base));
        }
    }

    /**
     * Liquid glass pill: the bar keeps TikTok's own color around it, the tabs sit on a floating
     * frosted capsule with a sheen, a theme-colored rim and a glowing bubble behind the open tab.
     */
    static final class GlassBarDrawable extends Drawable {
        static int version;

        final int baseColor;
        final int builtFor;
        private final WeakReference<View> hostRef;
        private final float density;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pill = new RectF();
        private final RectF tab = new RectF();
        private final int[] hostLocation = new int[2];
        private final int[] tabLocation = new int[2];

        GlassBarDrawable(View host, int baseColor) {
            this.baseColor = baseColor;
            this.builtFor = version;
            this.hostRef = new WeakReference<>(host);
            density = host.getResources().getDisplayMetrics().density;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, 1.1f * density));
        }

        static boolean isLight(int color) {
            double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
            return luminance > 0.6;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            PrismTheme theme = PrismTheme.current();
            View host = hostRef.get();
            int opaqueBase = Color.rgb(Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            boolean light = isLight(opaqueBase);

            // Around the pill: TikTok's own bar color, so nothing behind leaks through.
            fill.setShader(null);
            fill.setColor(opaqueBase);
            canvas.drawRect(bounds, fill);

            // The pill covers the tab area only, not the system navigation inset below it.
            int bottomInset = host == null ? 0 : host.getPaddingBottom();
            float side = 10 * density;
            float vertical = 3 * density;
            pill.set(bounds.left + side, bounds.top + vertical,
                    bounds.right - side, bounds.bottom - bottomInset - vertical);
            if (pill.height() < 24 * density) {
                pill.set(bounds.left + side, bounds.top + 1, bounds.right - side, bounds.bottom - bottomInset - 1);
            }
            float radius = pill.height() / 2f;

            // Soft drop shadow lifts the pill off the bar.
            shadow.setColor(Color.argb(light ? 34 : 120, 0, 0, 0));
            canvas.drawRoundRect(pill.left, pill.top + 2 * density, pill.right, pill.bottom + 2 * density,
                    radius, radius, shadow);

            int glass = PrismTheme.blend(light ? PrismTheme.blend(opaqueBase, Color.BLACK, 0.05f)
                    : PrismTheme.blend(opaqueBase, Color.WHITE, 0.11f), theme.accent, light ? 0.06f : 0.12f);
            fill.setShader(new LinearGradient(0, pill.top, 0, pill.bottom,
                    PrismTheme.blend(glass, Color.WHITE, light ? 0.30f : 0.06f), glass, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(pill, radius, radius, fill);

            drawSelectedBubble(canvas, host, theme, light, radius);

            sheen.setShader(new LinearGradient(0, pill.top, 0, pill.top + pill.height() * 0.55f,
                    Color.argb(light ? 110 : 40, 255, 255, 255), Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(pill, radius, radius, sheen);

            rim.setShader(new LinearGradient(pill.left, pill.top, pill.right, pill.bottom,
                    new int[]{
                            PrismTheme.withAlpha(theme.accent, 0.9f),
                            PrismTheme.withAlpha(Color.WHITE, light ? 0.85f : 0.35f),
                            PrismTheme.withAlpha(theme.accent2, 0.9f)
                    },
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
            float inset = rim.getStrokeWidth() / 2f;
            pill.inset(inset, inset);
            canvas.drawRoundRect(pill, radius - inset, radius - inset, rim);
        }

        private void drawSelectedBubble(Canvas canvas, @Nullable View host, PrismTheme theme,
                                        boolean light, float radius) {
            View selected = host == null ? null : selectedTab(host);
            if (selected == null) {
                return;
            }
            host.getLocationOnScreen(hostLocation);
            selected.getLocationOnScreen(tabLocation);
            float left = tabLocation[0] - hostLocation[0];
            float center = left + selected.getWidth() / 2f;
            float half = Math.min(selected.getWidth() / 2f - 2 * density, 38 * density);
            tab.set(center - half, pill.top + 3 * density, center + half, pill.bottom - 3 * density);
            tab.left = Math.max(tab.left, pill.left + 3 * density);
            tab.right = Math.min(tab.right, pill.right - 3 * density);
            float tabRadius = Math.min(radius, tab.height() / 2f);
            bubble.setShader(new LinearGradient(tab.left, tab.top, tab.right, tab.bottom,
                    PrismTheme.withAlpha(theme.accent, light ? 0.20f : 0.34f),
                    PrismTheme.withAlpha(theme.accent2, light ? 0.16f : 0.26f),
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(tab, tabRadius, tabRadius, bubble);
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

    /** The tab whose view (or a child of it) is selected, found below the bar's tab row. */
    @Nullable
    static View selectedTab(View bar) {
        ViewGroup row = tabRow(bar);
        if (row == null) {
            return null;
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && isSelectedDeep(child, 3)) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    private static ViewGroup tabRow(View view) {
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        int visible = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getVisibility() == View.VISIBLE) {
                visible++;
            }
        }
        if (visible >= 4 && visible <= 6) {
            return group;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = tabRow(group.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean isSelectedDeep(View view, int depth) {
        if (view.isSelected()) {
            return true;
        }
        if (depth == 0 || !(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (isSelectedDeep(group.getChildAt(i), depth - 1)) {
                return true;
            }
        }
        return false;
    }
}
