package app.morphe.extension.tiktok.prism;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Theme decorations in two corners: top right, just under the status bar, and bottom left,
 * just above the bottom bar (or the system navigation where there is no bottom bar).
 * <p>
 * Each theme is a small scene: a main piece per corner (pre-rendered images in
 * res/drawable-nodpi/prism_decor_*.png, made by tools/prism-decor/generate_decor.py), a soft
 * colored light behind it, and a few drifting particles (embers, hearts, petals, gold leaf,
 * bubbles, bokeh). Everything is a bitmap drawn with a matrix, so a frame costs a couple of
 * dozen GPU quads and nothing else.
 * <p>
 * Placement rules, so the pieces never jump around:
 * <ul>
 *   <li>The pieces are pinned to the screen corners and are never pushed aside by other
 *   views. Over a text input they fade out instead of moving.</li>
 *   <li>When the anchor changes (the bottom bar appears or hides, a new screen opens), the
 *   pieces glide to the new place instead of snapping.</li>
 *   <li>Only the activity in front draws. A screen that is paused under a see-through one,
 *   a floating window and an open keyboard all hide the decorations.</li>
 * </ul>
 */
public final class PrismDecor extends Drawable implements Runnable {
    private static final int FRAME_MS = 33;
    private static final int FRAME_MS_SAVER = 100;
    private static final int TAG = 0x4D455432;

    /** Where the web's hub is in prism_decor_web_tr, and where the spider holds its thread. */
    private static final float WEB_HUB_X = 0.66f;
    private static final float WEB_HUB_Y = 0.30f;
    private static final float SPIDER_ATTACH_Y = 0.18f;
    /** The images are drawn for 3 pixels per dp. */
    private static final float IMAGE_DENSITY = 3f;

    private static final Map<String, Bitmap> images = new HashMap<>();
    private static final Map<Integer, ColorFilter> tints = new HashMap<>();
    private static PrismTheme imagesTheme;

    private final WeakReference<View> hostRef;
    private final float dp;
    private final float statusBar;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Matrix hubMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final float[] point = new float[2];
    private int bottomInset;
    private int topInset;
    private boolean frameQueued;
    /** Rectangles (left, top, right, bottom) of text inputs. */
    private float[] blocked = new float[0];
    private float topRightAlpha = 1f;
    private float bottomLeftAlpha = 1f;

    /** Anchors as drawn: they ease toward the measured ones, so a change is a glide. */
    private float anchorTop = -1f;
    private float anchorBottom = -1f;
    private boolean active = true;
    private boolean keyboard;
    /** Fades the whole layer in and out (screen change, keyboard). */
    private float master;
    private boolean powerSave;
    private long powerCheckedAt;

    private PrismDecor(View host) {
        hostRef = new WeakReference<>(host);
        Resources resources = host.getResources();
        dp = resources.getDisplayMetrics().density;
        int id = resources.getIdentifier("status_bar_height", "dimen", "android");
        statusBar = id != 0 ? resources.getDimensionPixelSize(id) : 28 * dp;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Draws through the decor view's overlay, not as a child view: the overlay is outside the
     * view hierarchy, so TikTok never sees anything covering its player.
     */
    static PrismDecor attach(View decor) {
        Object tag = decor.getTag(TAG);
        PrismDecor layer;
        if (tag instanceof PrismDecor) {
            layer = (PrismDecor) tag;
        } else {
            layer = new PrismDecor(decor);
            decor.setTag(TAG, layer);
            decor.getOverlay().add(layer);
        }
        if (layer.getBounds().width() != decor.getWidth() || layer.getBounds().height() != decor.getHeight()) {
            layer.setBounds(0, 0, decor.getWidth(), decor.getHeight());
        }
        return layer;
    }

    @Nullable
    static PrismDecor existing(View decor) {
        Object tag = decor.getTag(TAG);
        return tag instanceof PrismDecor ? (PrismDecor) tag : null;
    }

    /** Space taken by the status bar and by the bottom bar (and system navigation). */
    void setInsets(int top, int bottom) {
        if (top != topInset || bottom != bottomInset) {
            topInset = top;
            bottomInset = bottom;
            invalidateSelf();
        }
    }

    void setObstacles(float[] blocked) {
        if (!java.util.Arrays.equals(blocked, this.blocked)) {
            this.blocked = blocked;
            invalidateSelf();
        }
    }

    /** Only the resumed, front activity shows decorations. */
    void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            invalidateSelf();
        }
    }

    void setKeyboard(boolean keyboard) {
        if (this.keyboard != keyboard) {
            this.keyboard = keyboard;
            invalidateSelf();
        }
    }

    static boolean enabled() {
        try {
            return Settings.PRISM_DECOR.get() && PrismTheme.current() != PrismTheme.CLASSIC;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void run() {
        frameQueued = false;
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // region images

    private Bitmap image(String name) {
        PrismTheme theme = PrismTheme.current();
        if (theme != imagesTheme) {
            // Only the current theme's images stay in memory.
            images.clear();
            imagesTheme = theme;
        }
        if (images.containsKey(name)) {
            return images.get(name);
        }
        Bitmap bitmap = null;
        View host = hostRef.get();
        if (host != null) {
            try {
                Resources resources = host.getResources();
                int id = resources.getIdentifier("prism_decor_" + name, "drawable", host.getContext().getPackageName());
                if (id != 0) {
                    bitmap = BitmapFactory.decodeResource(resources, id);
                    if (bitmap != null) {
                        bitmap.prepareToDraw();
                    }
                }
            } catch (Throwable ignored) {
                // Missing image: that piece is just not drawn.
            }
        }
        images.put(name, bitmap);
        return bitmap;
    }

    /** Scale that shows an image at its designed size, but no wider than {@code maxWidth}. */
    private float scaleFor(Bitmap bitmap, float maxWidth) {
        float natural = dp / IMAGE_DENSITY;
        return Math.min(natural, maxWidth / bitmap.getWidth());
    }

    private static ColorFilter tint(int color) {
        ColorFilter filter = tints.get(color);
        if (filter == null) {
            filter = new LightingColorFilter(color | 0xFF000000, 0);
            tints.put(color, filter);
        }
        return filter;
    }

    // endregion

    // region frame

    private static float ease(float current, float target) {
        if (current < 0) {
            return target;
        }
        float next = current + (target - current) * 0.22f;
        return Math.abs(next - target) < 0.5f ? target : next;
    }

    private boolean powerSave() {
        long now = SystemClock.uptimeMillis();
        if (now - powerCheckedAt > 5000) {
            powerCheckedAt = now;
            View host = hostRef.get();
            try {
                PowerManager pm = host == null ? null
                        : (PowerManager) host.getContext().getSystemService(Context.POWER_SERVICE);
                powerSave = pm != null && pm.isPowerSaveMode();
            } catch (Throwable ignored) {
                powerSave = false;
            }
        }
        return powerSave;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!enabled()) {
            master = 0f;
            return;
        }
        float w = getBounds().width();
        float h = getBounds().height();
        // Never above the status bar's lower edge: the clock, the camera and the icons are there.
        anchorTop = ease(anchorTop, Math.max(topInset, statusBar));
        anchorBottom = ease(anchorBottom, h - bottomInset);
        float targetMaster = active && !keyboard ? 1f : 0f;
        float nextMaster = master + (targetMaster - master) * 0.2f;
        master = Math.abs(nextMaster - targetMaster) < 0.02f ? targetMaster : nextMaster;

        float top = anchorTop;
        float bottom = anchorBottom;
        boolean animating = master > 0f || targetMaster > 0f;
        if (master > 0f && w > 0 && bottom - top >= 200 * dp) {
            float t = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f;
            boolean particles = !powerSave();
            canvas.save();
            canvas.clipRect(0, top, w, bottom);
            try {
                scene(canvas, w, top, bottom, t, particles);
            } catch (Throwable ignored) {
                // Decorations must never break TikTok's drawing.
            }
            canvas.restore();
            paint.setColorFilter(null);
        }
        View host = hostRef.get();
        if (animating && !frameQueued && host != null && host.isShown()) {
            frameQueued = true;
            host.postDelayed(this, powerSave ? FRAME_MS_SAVER : FRAME_MS);
        }
    }

    private void scene(Canvas canvas, float w, float top, float bottom, float t, boolean particles) {
        // Corner areas, for fading over text inputs.
        rect.set(w * 0.55f, top, w, top + 200 * dp);
        topRightAlpha = fade(topRightAlpha, rect);
        rect.set(0, bottom - 170 * dp, w * 0.5f, bottom);
        bottomLeftAlpha = fade(bottomLeftAlpha, rect);
        float tr = topRightAlpha * master;
        float bl = bottomLeftAlpha * master;

        switch (PrismTheme.current()) {
            case HALLOWEEN: {
                float flame = flame(t);
                aura(canvas, w, top, 170, 0x7B4DFF, 0.20f * breathe(t, 0.4f) * tr);
                halloweenWeb(canvas, w, top, t, tr);
                aura(canvas, 40 * dp, bottom - 30 * dp, 190, 0xFF7A1A, 0.30f * flame * bl);
                fog(canvas, w, bottom, t, bl);
                bottomLeft(canvas, "web_bl", bottom, 0.42f * w, 0f, 1.4f, 0f, bl, t);
                // Lantern light from just off the corner, with embers rising out of it. Nothing
                // solid here: the video's caption and nickname sit in this corner.
                sprite(canvas, image("glow"), 0f, bottom, 150 * (0.92f + 0.08f * flame), 0f, 0.38f * flame * bl, 0xFF9A30);
                if (particles) {
                    particles(canvas, image("ember"), 7, 11, 0f, 70 * dp, bottom - 4 * dp, bottom - 190 * dp,
                            7, 0.4f, 4.2f, 10, 0f, false, 0, bl);
                }
                break;
            }
            case FEMBOY:
                aura(canvas, w, top, 160, 0xFF6FB5, 0.22f * breathe(t, 0.5f) * tr);
                hanging(canvas, "bow_tr", w, top, 0.36f * w, 0.55f, 0f, tr, t);
                if (particles) {
                    particles(canvas, image("sparkle"), 3, 21, w - 0.34f * w, w - 0.04f * w,
                            top + 30 * dp, top + 110 * dp, 13, 0.4f, 2.6f, 4, 40f, false, 0, tr);
                    particles(canvas, image("bokeh"), 5, 22, w * 0.45f, w, top + 40 * dp, top + 230 * dp,
                            16, 0.5f, 9f, 14, 0f, false, 0xFF9CCB, 0.35f * tr);
                }
                aura(canvas, 30 * dp, bottom - 30 * dp, 160, 0xFF4F9A, 0.22f * breathe(t + 1, 0.5f) * bl);
                bottomLeft(canvas, "hearts_bl", bottom, 0.34f * w, 0f, 0f,
                        (float) Math.sin(t * 1.4) * 3 * dp, bl, t);
                if (particles) {
                    particles(canvas, image("heart"), 5, 23, 10 * dp, 0.36f * w, bottom - 40 * dp,
                            bottom - 230 * dp, 16, 0.35f, 5.5f, 12, 25f, false, 0, bl);
                }
                break;
            case NEON: {
                float flicker = flicker(t);
                float flicker2 = flicker(t + 3.7f);
                aura(canvas, w, top + 60 * dp, 170, 0xFF2BD6, 0.26f * flicker * tr);
                topRight(canvas, "neon_tr", w, top, 0.42f * w, 0f, 0.8f, 1f, flicker * tr, t);
                aura(canvas, 60 * dp, bottom - 50 * dp, 170, 0x2BFFE0, 0.24f * flicker2 * bl);
                bottomLeft(canvas, "neon_bl", bottom, 0.42f * w, 0f, 0f, 0f, flicker2 * bl, t);
                if (particles) {
                    particles(canvas, image("bokeh"), 4, 31, w * 0.55f, w, top + 20 * dp, top + 220 * dp,
                            12, 0.5f, 8f, 10, 0f, false, 0xFF4FE6, 0.4f * tr);
                    particles(canvas, image("bokeh"), 4, 32, 0, w * 0.45f, bottom - 20 * dp, bottom - 200 * dp,
                            12, 0.5f, 8f, 10, 0f, false, 0x4FFFEA, 0.4f * bl);
                }
                break;
            }
            case OCEAN:
                aura(canvas, w, top, 200, 0x6FD8FF, 0.20f * breathe(t, 0.35f) * tr);
                topRight(canvas, "rays_tr", w, top, 0.55f * w, 1.5f, 1f, 0f,
                        (0.75f + 0.25f * (float) Math.sin(t * 0.7)) * tr, t);
                aura(canvas, 30 * dp, bottom - 20 * dp, 170, 0x2F8CFF, 0.20f * breathe(t + 2, 0.35f) * bl);
                if (particles) {
                    particles(canvas, image("bubble"), 8, 41, 8 * dp, 110 * dp, bottom - 6 * dp,
                            bottom - 220 * dp, 14, 0.5f, 6f, 6, 0f, false, 0, bl);
                    particles(canvas, image("bubble"), 4, 42, w - 110 * dp, w - 14 * dp, top + 170 * dp,
                            top + 30 * dp, 11, 0.4f, 7f, 5, 0f, false, 0, 0.9f * tr);
                    particles(canvas, image("bokeh"), 6, 43, 0, w, bottom - 40 * dp, top + 120 * dp,
                            5, 0.6f, 16f, 10, 0f, false, 0x9FEFFF, 0.35f * master);
                }
                break;
            case GOLD:
                aura(canvas, w - 40 * dp, top + 60 * dp, 170, 0xFFC23D, 0.22f * breathe(t, 0.45f) * tr);
                hanging(canvas, "bauble_tr", w, top, 0.22f * w, 0.5f, 18 * dp, tr, t);
                aura(canvas, 30 * dp, bottom - 20 * dp, 170, 0xFFB020, 0.20f * breathe(t + 1.5f, 0.45f) * bl);
                bottomLeft(canvas, "glitter_bl", bottom, 0.38f * w, 0f, 0f, 0f, bl, t);
                sparkles(canvas, w, top, bottom, t);
                if (particles) {
                    particles(canvas, image("flake"), 7, 51, w * 0.5f, w - 10 * dp, top + 10 * dp,
                            top + 300 * dp, 9, 0.4f, 7f, 18, 90f, true, 0, tr);
                }
                break;
            case SAKURA:
                aura(canvas, w, top, 180, 0xFF8FB8, 0.20f * breathe(t, 0.4f) * tr);
                topRight(canvas, "branch_tr", w, top, 0.55f * w, 1.2f, 1f, 0.06f, tr, t);
                aura(canvas, 20 * dp, bottom - 10 * dp, 150, 0xFF9CC2, 0.16f * bl);
                bottomLeft(canvas, "petals_bl", bottom, 0.36f * w, 0f, 0f, 0f, bl, t);
                if (particles) {
                    particles(canvas, image("petal"), 9, 61, w * 0.3f, w - 20 * dp, top + 40 * dp,
                            top + 340 * dp, 12, 0.3f, 8f, 26, 120f, true, 0, tr);
                    particles(canvas, image("bokeh"), 4, 62, w * 0.5f, w, top + 60 * dp, top + 260 * dp,
                            14, 0.5f, 10f, 12, 0f, false, 0xFFB7D0, 0.3f * tr);
                }
                break;
            case PRISM: {
                int hueA = Color.HSVToColor(new float[]{(t * 24) % 360, 0.65f, 1f});
                int hueB = Color.HSVToColor(new float[]{(t * 24 + 180) % 360, 0.65f, 1f});
                aura(canvas, w, top + 30 * dp, 180, hueA, 0.22f * tr);
                topRight(canvas, "prism_tr", w, top, 0.5f * w, 0.6f, 1f, 0f,
                        (0.8f + 0.2f * (float) Math.sin(t * 1.3)) * tr, t);
                aura(canvas, 20 * dp, bottom - 20 * dp, 180, hueB, 0.22f * bl);
                bottomLeft(canvas, "leak_bl", bottom, 0.42f * w, 0f, 0f, 0f,
                        (0.6f + 0.4f * (float) Math.sin(t * 0.6)) * bl, t);
                if (particles) {
                    particles(canvas, image("bokeh"), 6, 71, w * 0.35f, w, top + 60 * dp, top + 280 * dp,
                            14, 0.5f, 10f, 12, 0f, false, hueB, 0.35f * tr);
                    particles(canvas, image("bokeh"), 4, 72, 0, w * 0.4f, bottom - 30 * dp, bottom - 200 * dp,
                            12, 0.5f, 10f, 12, 0f, false, hueA, 0.35f * bl);
                }
                break;
            }
            default:
                break;
        }
    }

    // endregion

    // region pieces

    /** Gentle sway, like something light hanging in moving air. */
    private static float sway(float t, float degrees) {
        return degrees * ((float) Math.sin(t * 1.05) * 0.75f + (float) Math.sin(t * 2.3 + 1) * 0.25f);
    }

    private static float breathe(float t, float speed) {
        return 0.75f + 0.25f * (float) Math.sin(t * speed * Math.PI);
    }

    private static float flicker(float t) {
        // Mostly steady, with a short stutter every few seconds like a real neon tube.
        float cycle = t % 6.5f;
        return cycle > 5.9f && Math.sin(t * 60) > 0 ? 0.45f : 0.95f;
    }

    /** Candle light: a slow wobble with small fast jitter. */
    private static float flame(float t) {
        return 0.78f + 0.12f * (float) Math.sin(t * 2.1) + 0.06f * (float) Math.sin(t * 7.3)
                + 0.04f * (float) Math.sin(t * 13.7);
    }

    private void setAlpha(float alpha) {
        paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
    }

    /** A soft colored light centered on (x, y). */
    private void aura(Canvas canvas, float x, float y, float radiusDp, int color, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        sprite(canvas, image("glow"), x, y, radiusDp * 2, 0f, alpha, color);
    }

    /**
     * A piece in the top-right corner, swaying around a point given as a fraction of the image
     * (the corner by default). Returns the image's scale, or 0 if it was not drawn.
     */
    private float topRight(Canvas canvas, String name, float w, float top, float maxWidth, float degrees,
                           float pivotX, float pivotY, float alpha, float t) {
        Bitmap bitmap = image(name);
        if (bitmap == null || alpha <= 0f) {
            return 0f;
        }
        float scale = scaleFor(bitmap, maxWidth);
        float left = w - bitmap.getWidth() * scale;
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, top);
        matrix.postRotate(sway(t, degrees), left + bitmap.getWidth() * scale * pivotX,
                top + bitmap.getHeight() * scale * pivotY);
        paint.setColorFilter(null);
        setAlpha(alpha);
        canvas.drawBitmap(bitmap, matrix, paint);
        return scale;
    }

    /** Something hanging from the top edge on a ribbon or chain, swinging around that point. */
    private void hanging(Canvas canvas, String name, float w, float top, float maxWidth, float hangX,
                         float rightGap, float alpha, float t) {
        Bitmap bitmap = image(name);
        if (bitmap == null || alpha <= 0f) {
            return;
        }
        float scale = scaleFor(bitmap, maxWidth);
        float width = bitmap.getWidth() * scale;
        float left = w - width - rightGap;
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, top);
        matrix.postRotate(sway(t, 4f), left + width * hangX, top);
        paint.setColorFilter(null);
        setAlpha(alpha);
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    /** A piece standing in the bottom-left corner. Returns its scale, or 0 if not drawn. */
    private float bottomLeft(Canvas canvas, String name, float bottom, float maxWidth, float left,
                             float degrees, float lift, float alpha, float t) {
        Bitmap bitmap = image(name);
        if (bitmap == null || alpha <= 0f) {
            return 0f;
        }
        float scale = scaleFor(bitmap, maxWidth);
        float y = bottom - bitmap.getHeight() * scale - lift;
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, y);
        matrix.postRotate(sway(t + 2f, degrees), left, bottom);
        paint.setColorFilter(null);
        setAlpha(alpha);
        canvas.drawBitmap(bitmap, matrix, paint);
        return scale;
    }

    /** Eases a corner's visibility toward hidden while it covers a text input. */
    private float fade(float current, RectF area) {
        float target = 1f;
        for (int i = 0; i + 3 < blocked.length; i += 4) {
            if (area.left < blocked[i + 2] && blocked[i] < area.right
                    && area.top < blocked[i + 3] && blocked[i + 1] < area.bottom) {
                target = 0f;
                break;
            }
        }
        float next = current + (target - current) * 0.15f;
        return Math.abs(next - target) < 0.02f ? target : next;
    }

    private void halloweenWeb(Canvas canvas, float w, float top, float t, float alpha) {
        float swayDegrees = 1.1f;
        float scale = topRight(canvas, "web_tr", w, top, 0.42f * w, swayDegrees, 1f, 0f, alpha, t);
        Bitmap web = image("web_tr");
        Bitmap spider = image("spider");
        if (scale <= 0f || web == null || spider == null) {
            return;
        }
        // The hub moves with the swaying web; the spider hangs below it on its own thread.
        float webWidth = web.getWidth() * scale;
        float webHeight = web.getHeight() * scale;
        point[0] = w - webWidth + webWidth * WEB_HUB_X;
        point[1] = top + webHeight * WEB_HUB_Y;
        hubMatrix.setRotate(sway(t, swayDegrees), w, top);
        hubMatrix.mapPoints(point);
        float hubX = point[0];
        float hubY = point[1];
        float length = webHeight * (0.42f + 0.16f * (float) Math.sin(t * 0.55));
        float swing = (float) Math.sin(t * 1.45) * 3.5f + (float) Math.sin(t * 0.6) * 1.5f;
        canvas.save();
        canvas.rotate(swing, hubX, hubY);
        line.setStrokeWidth(Math.max(1f, 0.45f * dp));
        line.setColor(0xD0C8C8D0);
        line.setAlpha((int) (200 * alpha));
        canvas.drawLine(hubX, hubY, hubX, hubY + length, line);
        float spiderScale = scale * 1.1f;
        matrix.setScale(spiderScale, spiderScale);
        matrix.postTranslate(hubX - spider.getWidth() * spiderScale / 2f,
                hubY + length - spider.getHeight() * spiderScale * SPIDER_ATTACH_Y);
        paint.setColorFilter(null);
        setAlpha(alpha);
        canvas.drawBitmap(spider, matrix, paint);
        canvas.restore();
    }

    /** Two copies of a mist wisp sliding slowly along the bottom-left corner. */
    private void fog(Canvas canvas, float w, float bottom, float t, float alpha) {
        Bitmap fog = image("fog");
        if (fog == null || alpha <= 0f) {
            return;
        }
        float scale = 0.75f * w / fog.getWidth();
        float fogWidth = fog.getWidth() * scale;
        float y = bottom - fog.getHeight() * scale * 0.85f;
        float offset = (t * 9 * dp) % fogWidth;
        paint.setColorFilter(tint(0xD8D0FF));
        for (int k = 0; k < 2; k++) {
            float x = -fogWidth * 0.55f + offset - k * fogWidth;
            // Fade each copy in and out at the ends of its path so nothing pops.
            float travel = (x + fogWidth) / (fogWidth * 1.1f);
            float edge = Math.max(0f, Math.min(1f, Math.min(travel, 1.4f - travel) * 2.5f));
            matrix.setScale(scale, scale);
            matrix.postTranslate(x, y + (float) Math.sin(t * 0.4 + k) * 3 * dp);
            setAlpha(0.55f * alpha * edge);
            canvas.drawBitmap(fog, matrix, paint);
        }
        paint.setColorFilter(null);
    }

    /** Draws a small sprite centered on (x, y), optionally tinted. */
    private void sprite(Canvas canvas, Bitmap bitmap, float x, float y, float sizeDp, float rotation,
                        float alpha, int tint) {
        if (bitmap == null || alpha <= 0.01f) {
            return;
        }
        float scale = sizeDp * dp / bitmap.getWidth();
        matrix.setScale(scale, scale);
        matrix.postTranslate(x - bitmap.getWidth() * scale / 2f, y - bitmap.getHeight() * scale / 2f);
        matrix.postRotate(rotation, x, y);
        paint.setColorFilter(tint == 0 ? null : tint(tint));
        setAlpha(alpha);
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    /** Stable pseudo-random number in [0, 1) for particle {@code i} of a group. */
    private static float rnd(int seed, int i, int k) {
        int x = seed * 73856093 ^ i * 19349663 ^ k * 83492791;
        x ^= x >>> 13;
        x *= 0x5bd1e995;
        x ^= x >>> 15;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * A group of sprites travelling from {@code yFrom} to {@code yTo} (up or down) between
     * {@code x0} and {@code x1}, swaying sideways and fading in and out at the ends. With
     * {@code tumble} they flip around their long axis like a falling leaf.
     */
    private void particles(Canvas canvas, Bitmap bitmap, int count, int seed, float x0, float x1,
                           float yFrom, float yTo, float sizeDp, float sizeJitter, float cycleSeconds,
                           float wobbleDp, float spinDegrees, boolean tumble, int tint, float alpha) {
        if (bitmap == null || alpha <= 0.01f) {
            return;
        }
        float t = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f;
        paint.setColorFilter(tint == 0 ? null : tint(tint));
        for (int i = 0; i < count; i++) {
            float r1 = rnd(seed, i, 1);
            float r2 = rnd(seed, i, 2);
            float r3 = rnd(seed, i, 3);
            float r4 = rnd(seed, i, 4);
            float period = cycleSeconds * (0.75f + 0.5f * r1);
            float phase = ((t + r2 * period) / period) % 1f;
            float x = x0 + (x1 - x0) * r3 + (float) Math.sin(t * (0.5f + r4) + i * 1.7f) * wobbleDp * dp;
            float y = yFrom + (yTo - yFrom) * phase;
            float envelope = (float) Math.sin(Math.PI * phase);
            float size = sizeDp * (1f - sizeJitter + 2f * sizeJitter * r4) * dp;
            float scale = size / bitmap.getWidth();
            float scaleX = scale;
            if (tumble) {
                scaleX *= 0.2f + 0.8f * Math.abs((float) Math.cos(t * (1.2f + r1 * 1.5f) + i));
            }
            matrix.setScale(scaleX, scale);
            matrix.postTranslate(x - bitmap.getWidth() * scaleX / 2f, y - bitmap.getHeight() * scale / 2f);
            matrix.postRotate(r2 * 360f + t * spinDegrees * (r1 - 0.5f) * 2f, x, y);
            setAlpha(alpha * envelope * (0.6f + 0.4f * r3));
            canvas.drawBitmap(bitmap, matrix, paint);
        }
        paint.setColorFilter(null);
    }

    private void sparkles(Canvas canvas, float w, float top, float bottom, float t) {
        Bitmap sparkle = image("sparkle");
        if (sparkle == null) {
            return;
        }
        float[][] spots = {{0.12f, -38}, {0.26f, -22}, {0.06f, -14}, {-0.10f, 120}, {-0.18f, 70}, {-0.30f, 30}};
        for (int i = 0; i < spots.length; i++) {
            float twinkle = (float) Math.max(0, Math.sin(t * 1.6 + i * 1.9));
            float x = spots[i][0] > 0 ? w * spots[i][0] : w + w * spots[i][0];
            boolean low = spots[i][1] < 0;
            float y = low ? bottom + spots[i][1] * dp : top + spots[i][1] * dp;
            float alpha = (low ? bottomLeftAlpha : topRightAlpha) * master;
            sprite(canvas, sparkle, x, y, 10 + 14 * twinkle, t * 20, twinkle * alpha, 0);
        }
    }

    // endregion
}
