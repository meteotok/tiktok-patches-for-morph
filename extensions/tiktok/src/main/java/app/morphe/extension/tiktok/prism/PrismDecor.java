package app.morphe.extension.tiktok.prism;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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
 * just above the bottom bar. Halloween hangs a cobweb with a spider on its thread, the other
 * themes a bow, a neon sign, a gold bauble, a sakura branch and so on.
 * <p>
 * The pieces are pre-rendered images (res/drawable-nodpi/prism_decor_*.png, made by
 * tools/prism-decor/generate_decor.py) that only get rotated a little each frame, so the layer
 * costs next to nothing while videos play. It never takes touches, and a piece over a text
 * input fades out.
 */
public final class PrismDecor extends Drawable implements Runnable {
    private static final int FRAME_MS = 40;
    private static final int TAG = 0x4D455432;

    /** Where the web's hub is in prism_decor_web_tr, and where the spider holds its thread. */
    private static final float WEB_HUB_X = 0.66f;
    private static final float WEB_HUB_Y = 0.30f;
    private static final float SPIDER_ATTACH_Y = 0.18f;
    /** The images are drawn for 3 pixels per dp. */
    private static final float IMAGE_DENSITY = 3f;

    private static final Map<String, Bitmap> images = new HashMap<>();
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
    private int variant;
    /** Rectangles (left, top, right, bottom) of text inputs. */
    private float[] blocked = new float[0];
    private float topRightAlpha;
    /** Where the last top-right piece ended up after moving clear of inputs. */
    private float placedTop;
    private float bottomLeftAlpha;

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

    void setVariant(int variant) {
        this.variant = variant;
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

    // endregion

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!enabled()) {
            return;
        }
        float w = getBounds().width();
        float h = getBounds().height();
        // Never above the status bar's lower edge: the clock, the camera and the icons are there.
        float top = Math.max(topInset, statusBar);
        float bottom = h - bottomInset;
        if (w <= 0 || bottom - top < 200 * dp) {
            return;
        }
        float t = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f;
        canvas.save();
        canvas.clipRect(0, top, w, bottom);
        try {
            switch (PrismTheme.current()) {
                case HALLOWEEN:
                    halloween(canvas, w, top, bottom, t);
                    break;
                case FEMBOY:
                    hanging(canvas, "bow_tr", w, top, 0.36f * w, 0.55f, 0f, t);
                    bottomLeft(canvas, "hearts_bl", w, bottom, 0.34f * w, 0f, 0f,
                            (float) Math.sin(t * 1.4) * 3 * dp, 1f, t);
                    break;
                case NEON:
                    float flicker = flicker(t);
                    topRight(canvas, "neon_tr", w, top, 0.42f * w, 0f, 0.8f, 1f, flicker, t);
                    bottomLeft(canvas, "neon_bl", w, bottom, 0.42f * w, 0f, 0f, 0f, flicker(t + 3.7f), t);
                    break;
                case OCEAN:
                    topRight(canvas, "rays_tr", w, top, 0.55f * w, 1.5f, 1f, 0f,
                            0.75f + 0.25f * (float) Math.sin(t * 0.7), t);
                    bubbles(canvas, w, top, bottom, t);
                    break;
                case GOLD:
                    hanging(canvas, "bauble_tr", w, top, 0.22f * w, 0.5f, 18 * dp, t);
                    bottomLeft(canvas, "glitter_bl", w, bottom, 0.38f * w, 0f, 0f, 0f, 1f, t);
                    sparkles(canvas, w, top, bottom, t);
                    break;
                case SAKURA:
                    topRight(canvas, "branch_tr", w, top, 0.55f * w, 1.2f, 1f, 0.06f, 1f, t);
                    bottomLeft(canvas, "petals_bl", w, bottom, 0.36f * w, 0f, 0f, 0f, 1f, t);
                    fallingPetals(canvas, w, top, bottom, t);
                    break;
                case PRISM:
                    topRight(canvas, "prism_tr", w, top, 0.5f * w, 0.6f, 1f, 0f,
                            0.8f + 0.2f * (float) Math.sin(t * 1.3), t);
                    bottomLeft(canvas, "leak_bl", w, bottom, 0.42f * w, 0f, 0f, 0f,
                            0.6f + 0.4f * (float) Math.sin(t * 0.6), t);
                    break;
                default:
                    break;
            }
        } catch (Throwable ignored) {
            // Decorations must never break TikTok's drawing.
        }
        canvas.restore();
        View host = hostRef.get();
        if (!frameQueued && host != null && host.isShown()) {
            frameQueued = true;
            host.postDelayed(this, FRAME_MS);
        }
    }

    // region pieces

    /** Gentle sway, like something light hanging in moving air. */
    private static float sway(float t, float degrees) {
        return degrees * ((float) Math.sin(t * 1.05) * 0.75f + (float) Math.sin(t * 2.3 + 1) * 0.25f);
    }

    private static float flicker(float t) {
        // Mostly steady, with a short stutter every few seconds like a real neon tube.
        float cycle = t % 6.5f;
        return cycle > 5.9f && Math.sin(t * 60) > 0 ? 0.45f : 0.95f;
    }

    /**
     * A piece in the top-right corner, swaying around a point given as a fraction of the image
     * (the corner by default). Returns the image's scale, or 0 if it was not drawn.
     */
    private float topRight(Canvas canvas, String name, float w, float top, float maxWidth, float degrees,
                           float pivotX, float pivotY, float alpha, float t) {
        Bitmap bitmap = image(name);
        if (bitmap == null) {
            return 0f;
        }
        float scale = scaleFor(bitmap, maxWidth);
        float left = w - bitmap.getWidth() * scale;
        top = belowInputs(left, w, bitmap.getHeight() * scale, top);
        rect.set(left, top, w, top + bitmap.getHeight() * scale);
        topRightAlpha = fade(topRightAlpha, rect);
        placedTop = top;
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, top);
        matrix.postRotate(sway(t, degrees), left + bitmap.getWidth() * scale * pivotX,
                top + bitmap.getHeight() * scale * pivotY);
        paint.setAlpha((int) (255 * alpha * topRightAlpha));
        canvas.drawBitmap(bitmap, matrix, paint);
        return scale;
    }

    /** Something hanging from the top edge on a ribbon or chain, swinging around that point. */
    private void hanging(Canvas canvas, String name, float w, float top, float maxWidth, float hangX,
                         float rightGap, float t) {
        Bitmap bitmap = image(name);
        if (bitmap == null) {
            return;
        }
        float scale = scaleFor(bitmap, maxWidth);
        float width = bitmap.getWidth() * scale;
        float left = w - width - rightGap;
        top = belowInputs(left, left + width, bitmap.getHeight() * scale, top);
        rect.set(left, top, left + width, top + bitmap.getHeight() * scale);
        topRightAlpha = fade(topRightAlpha, rect);
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, top);
        matrix.postRotate(sway(t, 4f), left + width * hangX, top);
        paint.setAlpha((int) (255 * topRightAlpha));
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    private void bottomLeft(Canvas canvas, String name, float w, float bottom, float maxWidth, float degrees,
                            float pivotX, float lift, float alpha, float t) {
        Bitmap bitmap = image(name);
        if (bitmap == null) {
            return;
        }
        float scale = scaleFor(bitmap, maxWidth);
        float height = bitmap.getHeight() * scale;
        bottom = aboveInputs(0, bitmap.getWidth() * scale, height, bottom);
        float y = bottom - height - lift;
        rect.set(0, y, bitmap.getWidth() * scale, y + height);
        bottomLeftAlpha = fade(bottomLeftAlpha, rect);
        matrix.setScale(scale, scale);
        matrix.postTranslate(0, y);
        matrix.postRotate(sway(t + 2f, degrees), 0, bottom);
        paint.setAlpha((int) (255 * alpha * bottomLeftAlpha));
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    /**
     * Lowest bottom edge for a bottom-left piece that keeps it clear of text inputs, such as
     * the comment box under the video: the piece sits on top of the input instead.
     */
    private float aboveInputs(float left, float right, float height, float bottom) {
        for (int pass = 0; pass < 3; pass++) {
            boolean moved = false;
            for (int i = 0; i + 3 < blocked.length; i += 4) {
                if (left < blocked[i + 2] && blocked[i] < right
                        && bottom - height < blocked[i + 3] && blocked[i + 1] < bottom) {
                    bottom = blocked[i + 1] - 2 * dp;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        return bottom;
    }

    /** Highest top edge for a top-right piece that keeps it clear of text inputs (search). */
    private float belowInputs(float left, float right, float height, float top) {
        for (int pass = 0; pass < 3; pass++) {
            boolean moved = false;
            for (int i = 0; i + 3 < blocked.length; i += 4) {
                if (left < blocked[i + 2] && blocked[i] < right
                        && top < blocked[i + 3] && blocked[i + 1] < top + height) {
                    top = blocked[i + 3] + 2 * dp;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        return top;
    }

    /** Eases a piece's visibility toward hidden when it still covers a text input. */
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

    private void halloween(Canvas canvas, float w, float top, float bottom, float t) {
        float swayDegrees = 1.1f;
        float scale = topRight(canvas, "web_tr", w, top, 0.4f * w, swayDegrees, 1f, 0f, 1f, t);
        top = placedTop;
        bottomLeft(canvas, "web_bl", w, bottom, 0.5f * w, 1.4f, 0f, 0f, 1f, t);
        Bitmap web = image("web_tr");
        Bitmap spider = image("spider");
        if (scale <= 0f || web == null || spider == null || topRightAlpha <= 0f) {
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
        int alpha = (int) (255 * topRightAlpha);
        canvas.save();
        canvas.rotate(swing, hubX, hubY);
        line.setStrokeWidth(Math.max(1f, 0.45f * dp));
        line.setColor(0xD0C8C8D0);
        line.setAlpha((int) (200 * topRightAlpha));
        canvas.drawLine(hubX, hubY, hubX, hubY + length, line);
        float spiderScale = scale * 1.1f;
        matrix.setScale(spiderScale, spiderScale);
        matrix.postTranslate(hubX - spider.getWidth() * spiderScale / 2f,
                hubY + length - spider.getHeight() * spiderScale * SPIDER_ATTACH_Y);
        paint.setAlpha(alpha);
        canvas.drawBitmap(spider, matrix, paint);
        canvas.restore();
    }

    /** Draws a small sprite centered on (x, y). */
    private void sprite(Canvas canvas, Bitmap bitmap, float x, float y, float sizeDp, float rotation, float alpha) {
        float scale = sizeDp * dp / bitmap.getWidth();
        matrix.setScale(scale, scale);
        matrix.postTranslate(x - bitmap.getWidth() * scale / 2f, y - bitmap.getHeight() * scale / 2f);
        matrix.postRotate(rotation, x, y);
        paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    private void bubbles(Canvas canvas, float w, float top, float bottom, float t) {
        Bitmap bubble = image("bubble");
        if (bubble == null) {
            return;
        }
        // A column of bubbles rising from the bottom-left corner, and a few in the top right.
        bottom = aboveInputs(0, 100 * dp, 180 * dp, bottom);
        rect.set(0, bottom - 180 * dp, 100 * dp, bottom);
        bottomLeftAlpha = fade(bottomLeftAlpha, rect);
        for (int i = 0; i < 6; i++) {
            float phase = (t * (0.11f + i * 0.013f) + i * 0.37f) % 1f;
            float x = (14 + (i * 23) % 70) * dp + (float) Math.sin(t * 1.7 + i) * 5 * dp;
            float y = bottom - 8 * dp - phase * 170 * dp;
            sprite(canvas, bubble, x, y, 9 + (i * 7) % 15, 0f, Math.min(1f, (1 - phase) * 2f) * bottomLeftAlpha);
        }
        for (int i = 0; i < 3; i++) {
            float phase = (t * (0.08f + i * 0.02f) + i * 0.41f) % 1f;
            float x = w - (24 + i * 26) * dp + (float) Math.sin(t * 1.3 + i) * 4 * dp;
            float y = top + 140 * dp - phase * 120 * dp;
            sprite(canvas, bubble, x, y, 8 + i * 4, 0f, (float) Math.sin(Math.PI * phase) * 0.9f);
        }
    }

    private void sparkles(Canvas canvas, float w, float top, float bottom, float t) {
        Bitmap sparkle = image("sparkle");
        if (sparkle == null) {
            return;
        }
        float[][] spots = {{0.12f, -38}, {0.26f, -22}, {0.06f, -14}, {-0.10f, 120}, {-0.18f, 70}};
        for (int i = 0; i < spots.length; i++) {
            float twinkle = (float) Math.max(0, Math.sin(t * 1.6 + i * 1.9));
            float x = spots[i][0] > 0 ? w * spots[i][0] : w + w * spots[i][0];
            float y = spots[i][1] < 0 ? bottom + spots[i][1] * dp : top + spots[i][1] * dp;
            sprite(canvas, sparkle, x, y, 10 + 12 * twinkle, t * 20, twinkle);
        }
    }

    private void fallingPetals(Canvas canvas, float w, float top, float bottom, float t) {
        Bitmap petal = image("petal");
        if (petal == null) {
            return;
        }
        // Petals let go of the branch and drift down the right side, fading out.
        for (int i = 0; i < 5; i++) {
            float phase = (t * (0.07f + i * 0.012f) + i * 0.23f) % 1f;
            float x = w - (40 + i * 22) * dp - phase * 50 * dp + (float) Math.sin(t * 1.2 + i * 2) * 12 * dp;
            float y = top + 50 * dp + phase * 260 * dp;
            float alpha = phase < 0.1f ? phase * 10 : (1 - phase) * 1.4f;
            sprite(canvas, petal, x, y, 10 + (i % 3) * 2, t * 60 + i * 70, alpha);
        }
    }

    // endregion
}
