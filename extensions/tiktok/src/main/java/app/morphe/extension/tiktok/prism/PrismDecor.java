package app.morphe.extension.tiktok.prism;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Theme decorations drawn over every TikTok screen: cobwebs, spiders and pumpkins for Halloween,
 * hearts and bows for Femboy, petals for Sakura and so on. The layer never takes touches and
 * keeps to the screen edges so videos, buttons and captions stay usable.
 */
public final class PrismDecor extends Drawable implements Runnable {
    private static final int FRAME_MS = 40;

    private final WeakReference<View> hostRef;
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private int bottomInset;
    private int topInset;
    private boolean frameQueued;
    /** Which arrangement this screen gets, so every page looks a little different. */
    private int variant;

    private PrismDecor(View host) {
        hostRef = new WeakReference<>(host);
        dp = host.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
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

    private static final int TAG = 0x4D455432;

    void setVariant(int variant) {
        if (variant != this.variant) {
            this.variant = variant;
            invalidateSelf();
        }
    }

    /**
     * Free spots in the status bar strip, as fractions of the width: right of the clock and
     * date, either side of the camera cutout, left of the system icons.
     */
    private static final float[] SLOTS = {0.42f, 0.585f, 0.645f, 0.705f};

    private float slot(int index) {
        return SLOTS[Math.floorMod(index + variant, SLOTS.length)];
    }

    /** Space taken by the bottom bar (and system navigation), so ground decorations sit on it. */
    void setInsets(int top, int bottom) {
        if (top != topInset || bottom != bottomInset) {
            topInset = top;
            bottomInset = bottom;
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
    public void draw(@NonNull Canvas canvas) {
        if (!enabled()) {
            return;
        }
        float w = getBounds().width();
        float h = getBounds().height();
        if (w <= 0 || h <= 0) {
            return;
        }
        float t = (SystemClock.uptimeMillis() % 600_000L) / 1000f;
        // Everything stays inside the status bar strip and a hair below it: TikTok has no
        // other spot that is free on every screen (sheets, search, comments, the player).
        float ground = Math.max(topInset, 24 * dp);
        boolean animated;
        canvas.save();
        canvas.clipRect(0, 0, w, ground * 1.35f);
        try {
            switch (PrismTheme.current()) {
                case HALLOWEEN:
                    animated = drawHalloween(canvas, w, ground, t);
                    break;
                case FEMBOY:
                    animated = drawFemboy(canvas, w, ground, t);
                    break;
                case NEON:
                    animated = drawNeon(canvas, w, ground, t);
                    break;
                case OCEAN:
                    animated = drawOcean(canvas, w, ground, t);
                    break;
                case GOLD:
                    animated = drawGold(canvas, w, ground, t);
                    break;
                case SAKURA:
                    animated = drawSakura(canvas, w, ground, t);
                    break;
                case PRISM:
                    animated = drawPrism(canvas, w, ground, t);
                    break;
                default:
                    animated = false;
            }
        } catch (Throwable ignored) {
            // Decorations must never break TikTok's drawing.
            animated = false;
        }
        canvas.restore();
        View host = hostRef.get();
        if (animated && !frameQueued && host != null && host.isShown()) {
            frameQueued = true;
            host.postDelayed(this, FRAME_MS);
        }
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

    // region Halloween

    private boolean drawHalloween(Canvas canvas, float w, float band, float t) {
        bandFog(canvas, w, band, Color.rgb(160, 60, 255));

        cobweb(canvas, 0, 0, band * 1.45f, 1);
        cobweb(canvas, w, 0, band * 1.3f, -1);

        float flicker = 0.75f + 0.25f * (float) Math.abs(Math.sin(t * 6.3) * Math.sin(t * 2.1));
        float flap = (float) Math.sin(t * 9);
        float bob = (float) Math.sin(t * 1.4) * band * 0.05f;
        switch (Math.floorMod(variant, 4)) {
            case 0:
                bat(canvas, w * slot(0) + (float) Math.sin(t * 0.6) * band * 0.2f, band * 0.45f, band * 0.22f, flap);
                pumpkin(canvas, w * slot(1), band * 0.58f, band * 0.38f, flicker);
                candyBucket(canvas, w * slot(2) + band * 0.2f, band * 0.66f, band * 0.32f);
                break;
            case 1:
                candyBucket(canvas, w * slot(0), band * 0.66f, band * 0.32f);
                ghost(canvas, w * slot(2), band * 0.55f + bob, band * 0.34f);
                pumpkin(canvas, w * slot(3), band * 0.58f, band * 0.36f, flicker);
                break;
            case 2:
                pumpkin(canvas, w * slot(1), band * 0.62f, band * 0.3f, flicker);
                pumpkin(canvas, w * slot(2), band * 0.56f, band * 0.4f, flicker);
                bat(canvas, w * slot(3), band * 0.4f + bob, band * 0.2f, flap);
                break;
            default:
                ghost(canvas, w * slot(0), band * 0.55f + bob, band * 0.32f);
                pumpkin(canvas, w * slot(1), band * 0.58f, band * 0.38f, flicker);
                bat(canvas, w * slot(2) + (float) Math.cos(t * 0.5) * band * 0.2f, band * 0.42f, band * 0.2f, -flap);
                break;
        }

        // The little spider always hangs from the right web.
        float swing = (float) Math.sin(t * 1.6) * 6f;
        float x = w - band * 0.7f;
        float length = band * 0.5f + (float) Math.sin(t * 0.7) * band * 0.06f;
        canvas.save();
        canvas.rotate(swing, x, 0);
        stroke.setShader(null);
        stroke.setColor(Color.argb(190, 255, 255, 255));
        stroke.setStrokeWidth(0.9f * dp);
        canvas.drawLine(x, 0, x, length, stroke);
        spider(canvas, x, length + band * 0.18f, band / (52 * dp));
        canvas.restore();
        return true;
    }

    private void ghost(Canvas canvas, float x, float y, float r) {
        glow.setShader(new RadialGradient(x, y, r * 2f, Color.argb(110, 200, 210, 255),
                Color.argb(0, 200, 210, 255), Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, r * 2f, glow);
        path.reset();
        path.moveTo(x - r, y + r);
        path.lineTo(x - r, y - r * 0.1f);
        path.cubicTo(x - r, y - r * 1.4f, x + r, y - r * 1.4f, x + r, y - r * 0.1f);
        path.lineTo(x + r, y + r);
        for (int i = 0; i < 4; i++) {
            float x0 = x + r - i * r / 2f;
            path.quadTo(x0 - r / 8f, y + r * 0.7f, x0 - r / 4f, y + r);
            path.quadTo(x0 - r * 3 / 8f, y + r * 1.3f, x0 - r / 2f, y + r);
        }
        path.close();
        fill.setShader(new LinearGradient(x, y - r, x, y + r, Color.WHITE, Color.rgb(214, 214, 255),
                Shader.TileMode.CLAMP));
        canvas.drawPath(path, fill);
        fill.setShader(null);
        fill.setColor(Color.rgb(40, 24, 70));
        canvas.drawOval(x - r * 0.45f, y - r * 0.45f, x - r * 0.15f, y, fill);
        canvas.drawOval(x + r * 0.15f, y - r * 0.45f, x + r * 0.45f, y, fill);
        canvas.drawOval(x - r * 0.14f, y + r * 0.15f, x + r * 0.14f, y + r * 0.5f, fill);
    }

    private void cobweb(Canvas canvas, float cx, float cy, float radius, int direction) {
        stroke.setShader(null);
        stroke.setColor(Color.argb(150, 255, 255, 255));
        stroke.setStrokeWidth(1.1f * dp);
        int spokes = 6;
        float[] xs = new float[spokes];
        float[] ys = new float[spokes];
        for (int i = 0; i < spokes; i++) {
            double angle = Math.toRadians(90.0 * i / (spokes - 1));
            xs[i] = (float) Math.cos(angle) * direction;
            ys[i] = (float) Math.sin(angle);
            canvas.drawLine(cx, cy, cx + xs[i] * radius, cy + ys[i] * radius, stroke);
        }
        for (int ring = 1; ring <= 4; ring++) {
            float r = radius * ring / 4.4f;
            path.reset();
            path.moveTo(cx + xs[0] * r, cy + ys[0] * r);
            for (int i = 1; i < spokes; i++) {
                // Each thread sags toward the corner like a real web.
                float mx = cx + (xs[i - 1] + xs[i]) / 2f * r * 0.82f;
                float my = cy + (ys[i - 1] + ys[i]) / 2f * r * 0.82f;
                path.quadTo(mx, my, cx + xs[i] * r, cy + ys[i] * r);
            }
            canvas.drawPath(path, stroke);
        }
    }

    private void spider(Canvas canvas, float x, float y, float scale) {
        float s = dp * scale;
        stroke.setShader(null);
        stroke.setColor(Color.rgb(25, 18, 30));
        stroke.setStrokeWidth(2.2f * s);
        for (int side = -1; side <= 1; side += 2) {
            for (int leg = 0; leg < 4; leg++) {
                float startY = y - 3 * s + leg * 2.4f * s;
                float kneeX = x + side * (10 + leg) * s;
                float kneeY = startY - (6 - leg * 2.2f) * s;
                float footX = x + side * (15 + leg * 1.5f) * s;
                float footY = startY + (5 + leg * 1.5f) * s;
                path.reset();
                path.moveTo(x, startY);
                path.lineTo(kneeX, kneeY);
                path.lineTo(footX, footY);
                canvas.drawPath(path, stroke);
            }
        }
        fill.setShader(null);
        fill.setColor(Color.rgb(30, 20, 36));
        canvas.drawCircle(x, y + 2 * s, 8.5f * s, fill);
        canvas.drawCircle(x, y - 7 * s, 5.5f * s, fill);
        // Big cartoon eyes.
        fill.setColor(Color.WHITE);
        canvas.drawCircle(x - 2.4f * s, y - 7.5f * s, 2.3f * s, fill);
        canvas.drawCircle(x + 2.4f * s, y - 7.5f * s, 2.3f * s, fill);
        fill.setColor(Color.BLACK);
        canvas.drawCircle(x - 2.0f * s, y - 7.0f * s, 1.1f * s, fill);
        canvas.drawCircle(x + 2.8f * s, y - 7.0f * s, 1.1f * s, fill);
        // Orange hourglass mark.
        fill.setColor(Color.rgb(255, 122, 24));
        canvas.drawCircle(x, y + 3 * s, 2.2f * s, fill);
    }

    private void bat(Canvas canvas, float x, float y, float size, float flap) {
        float lift = flap * size * 0.45f;
        path.reset();
        path.moveTo(x, y);
        path.quadTo(x - size * 0.5f, y - size * 0.35f - lift, x - size * 1.4f, y - lift);
        path.quadTo(x - size * 1.0f, y + size * 0.05f, x - size * 0.95f, y + size * 0.3f);
        path.quadTo(x - size * 0.7f, y + size * 0.05f, x - size * 0.45f, y + size * 0.32f);
        path.quadTo(x - size * 0.3f, y + size * 0.1f, x, y + size * 0.35f);
        path.quadTo(x + size * 0.3f, y + size * 0.1f, x + size * 0.45f, y + size * 0.32f);
        path.quadTo(x + size * 0.7f, y + size * 0.05f, x + size * 0.95f, y + size * 0.3f);
        path.quadTo(x + size * 1.0f, y + size * 0.05f, x + size * 1.4f, y - lift);
        path.quadTo(x + size * 0.5f, y - size * 0.35f - lift, x, y);
        path.close();
        fill.setShader(null);
        fill.setColor(Color.rgb(36, 20, 48));
        canvas.drawPath(path, fill);
        canvas.drawCircle(x, y + size * 0.05f, size * 0.28f, fill);
        fill.setColor(Color.rgb(255, 200, 60));
        canvas.drawCircle(x - size * 0.1f, y, size * 0.06f, fill);
        canvas.drawCircle(x + size * 0.1f, y, size * 0.06f, fill);
    }

    private void pumpkinBody(Canvas canvas, float x, float y, float r) {
        int dark = Color.rgb(196, 78, 8);
        int light = Color.rgb(255, 146, 36);
        fill.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.4f, r * 1.4f, light, dark, Shader.TileMode.CLAMP));
        stroke.setShader(null);
        stroke.setColor(Color.rgb(140, 52, 4));
        stroke.setStrokeWidth(1.4f * dp);
        float[] offsets = {-0.55f, 0.55f, -0.25f, 0.25f, 0f};
        float[] widths = {0.6f, 0.6f, 0.62f, 0.62f, 0.6f};
        for (int i = 0; i < offsets.length; i++) {
            oval.set(x + offsets[i] * r - widths[i] * r, y - r * 0.82f,
                    x + offsets[i] * r + widths[i] * r, y + r * 0.82f);
            canvas.drawOval(oval, fill);
            canvas.drawOval(oval, stroke);
        }
        fill.setShader(null);
        // Stem with a curly vine.
        fill.setColor(Color.rgb(74, 122, 38));
        oval.set(x - r * 0.12f, y - r * 1.12f, x + r * 0.16f, y - r * 0.7f);
        canvas.drawRoundRect(oval, r * 0.08f, r * 0.08f, fill);
        stroke.setColor(Color.rgb(92, 150, 50));
        path.reset();
        path.moveTo(x + r * 0.1f, y - r * 0.9f);
        path.cubicTo(x + r * 0.5f, y - r * 1.2f, x + r * 0.6f, y - r * 0.7f, x + r * 0.35f, y - r * 0.8f);
        canvas.drawPath(path, stroke);
    }

    private void pumpkin(Canvas canvas, float x, float y, float r, float flicker) {
        // Candle glow behind the carved face.
        glow.setShader(new RadialGradient(x, y, r * 2.2f,
                Color.argb((int) (90 * flicker), 255, 170, 40), Color.argb(0, 255, 170, 40), Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, r * 2.2f, glow);
        pumpkinBody(canvas, x, y, r);
        fill.setShader(null);
        fill.setColor(Color.argb(255, 255, (int) (200 + 40 * flicker), 70));
        // Triangle eyes and nose.
        path.reset();
        path.moveTo(x - r * 0.5f, y - r * 0.05f);
        path.lineTo(x - r * 0.18f, y - r * 0.05f);
        path.lineTo(x - r * 0.34f, y - r * 0.4f);
        path.close();
        path.moveTo(x + r * 0.5f, y - r * 0.05f);
        path.lineTo(x + r * 0.18f, y - r * 0.05f);
        path.lineTo(x + r * 0.34f, y - r * 0.4f);
        path.close();
        path.moveTo(x - r * 0.08f, y + r * 0.12f);
        path.lineTo(x + r * 0.08f, y + r * 0.12f);
        path.lineTo(x, y);
        path.close();
        // Zigzag grin.
        path.moveTo(x - r * 0.58f, y + r * 0.25f);
        path.lineTo(x - r * 0.4f, y + r * 0.38f);
        path.lineTo(x - r * 0.25f, y + r * 0.28f);
        path.lineTo(x - r * 0.1f, y + r * 0.42f);
        path.lineTo(x + r * 0.05f, y + r * 0.3f);
        path.lineTo(x + r * 0.22f, y + r * 0.42f);
        path.lineTo(x + r * 0.38f, y + r * 0.28f);
        path.lineTo(x + r * 0.58f, y + r * 0.25f);
        path.quadTo(x, y + r * 0.85f, x - r * 0.58f, y + r * 0.25f);
        path.close();
        canvas.drawPath(path, fill);
    }

    private void candyBucket(Canvas canvas, float x, float y, float r) {
        // Candies spilling over the top.
        candy(canvas, x - r * 0.45f, y - r * 0.78f, r * 0.26f, Color.rgb(255, 64, 129), 20);
        candy(canvas, x + r * 0.4f, y - r * 0.82f, r * 0.24f, Color.rgb(124, 77, 255), -25);
        candy(canvas, x, y - r * 0.95f, r * 0.24f, Color.rgb(0, 200, 140), 5);
        // Lollipop.
        stroke.setShader(null);
        stroke.setColor(Color.rgb(250, 250, 250));
        stroke.setStrokeWidth(2f * dp);
        canvas.drawLine(x + r * 0.15f, y - r * 0.6f, x + r * 0.35f, y - r * 1.55f, stroke);
        fill.setShader(null);
        fill.setColor(Color.rgb(255, 214, 0));
        canvas.drawCircle(x + r * 0.37f, y - r * 1.62f, r * 0.26f, fill);
        stroke.setColor(Color.rgb(255, 87, 34));
        stroke.setStrokeWidth(1.4f * dp);
        oval.set(x + r * 0.22f, y - r * 1.77f, x + r * 0.52f, y - r * 1.47f);
        canvas.drawArc(oval, 0, 270, false, stroke);

        pumpkinBody(canvas, x, y, r);
        // Flat bucket rim and handle.
        fill.setShader(null);
        fill.setColor(Color.rgb(40, 22, 10));
        oval.set(x - r * 0.75f, y - r * 0.82f, x + r * 0.75f, y - r * 0.55f);
        canvas.drawOval(oval, fill);
        stroke.setColor(Color.rgb(40, 22, 10));
        stroke.setStrokeWidth(2.2f * dp);
        oval.set(x - r * 0.95f, y - r * 1.9f, x + r * 0.95f, y - r * 0.2f);
        canvas.drawArc(oval, 200, 140, false, stroke);
        // Friendly face.
        fill.setColor(Color.rgb(40, 22, 10));
        canvas.drawCircle(x - r * 0.3f, y - r * 0.05f, r * 0.11f, fill);
        canvas.drawCircle(x + r * 0.3f, y - r * 0.05f, r * 0.11f, fill);
        stroke.setStrokeWidth(1.8f * dp);
        oval.set(x - r * 0.35f, y - r * 0.05f, x + r * 0.35f, y + r * 0.45f);
        canvas.drawArc(oval, 20, 140, false, stroke);
    }

    private void candy(Canvas canvas, float x, float y, float r, int color, float rotation) {
        canvas.save();
        canvas.rotate(rotation, x, y);
        fill.setShader(null);
        fill.setColor(color);
        path.reset();
        path.moveTo(x - r, y);
        path.lineTo(x - r * 1.8f, y - r * 0.7f);
        path.lineTo(x - r * 1.8f, y + r * 0.7f);
        path.close();
        path.moveTo(x + r, y);
        path.lineTo(x + r * 1.8f, y - r * 0.7f);
        path.lineTo(x + r * 1.8f, y + r * 0.7f);
        path.close();
        canvas.drawPath(path, fill);
        canvas.drawCircle(x, y, r, fill);
        fill.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.3f, fill);
        canvas.restore();
    }

    // endregion

    // region other themes

    private boolean drawFemboy(Canvas canvas, float w, float band, float t) {
        bandFog(canvas, w, band, Color.rgb(255, 92, 170));
        bow(canvas, w * slot(1), band * 0.5f, band * 0.3f, Color.rgb(255, 92, 170));
        bow(canvas, w * slot(3), band * 0.55f, band * 0.22f, Color.rgb(120, 190, 255));
        for (int i = 0; i < 3; i++) {
            float phase = ((t * 0.25f) + i / 3f) % 1f;
            float x = w * slot(2) + (i - 1) * band * 0.4f;
            float y = band * (1.05f - phase * 0.85f);
            heart(canvas, x, y, band * 0.14f, PrismTheme.withAlpha(
                    i == 1 ? Color.rgb(120, 190, 255) : Color.rgb(255, 92, 170),
                    (float) Math.sin(Math.PI * phase)));
        }
        sparkles(canvas, w, band, t, Color.WHITE);
        return true;
    }

    private boolean drawNeon(Canvas canvas, float w, float band, float t) {
        bandFog(canvas, w, band, Color.rgb(255, 0, 229));
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 2.4);
        for (int layer = 2; layer >= 0; layer--) {
            stroke.setStrokeWidth((1.6f + layer * 3f) * dp);
            stroke.setShader(new LinearGradient(0, 0, w, 0,
                    PrismTheme.withAlpha(Color.rgb(57, 255, 20), layer == 0 ? 1f : 0.25f * pulse),
                    PrismTheme.withAlpha(Color.rgb(255, 0, 229), layer == 0 ? 1f : 0.25f * pulse),
                    Shader.TileMode.CLAMP));
            canvas.drawLine(0, band - 1.5f * dp, w, band - 1.5f * dp, stroke);
        }
        stroke.setShader(null);
        // A neon bolt that moves to a different spot on every screen.
        float x = w * slot(1);
        path.reset();
        path.moveTo(x + band * 0.1f, band * 0.12f);
        path.lineTo(x - band * 0.18f, band * 0.55f);
        path.lineTo(x + band * 0.02f, band * 0.55f);
        path.lineTo(x - band * 0.08f, band * 0.92f);
        path.lineTo(x + band * 0.22f, band * 0.42f);
        path.lineTo(x + band * 0.02f, band * 0.42f);
        path.close();
        glow.setShader(new RadialGradient(x, band * 0.5f, band * 0.7f,
                PrismTheme.withAlpha(Color.rgb(57, 255, 20), 0.45f * pulse), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, band * 0.5f, band * 0.7f, glow);
        fill.setShader(null);
        fill.setColor(Color.rgb(200, 255, 180));
        canvas.drawPath(path, fill);
        sparkles(canvas, w, band, t, Color.rgb(57, 255, 20));
        return true;
    }

    private boolean drawOcean(Canvas canvas, float w, float band, float t) {
        bandFog(canvas, w, band, Color.rgb(0, 170, 255));
        for (int i = 0; i < 8; i++) {
            float phase = ((t * (0.18f + (i % 3) * 0.05f)) + i / 8f) % 1f;
            float x = w * slot(i % 3 + 1) + (float) Math.sin(t * 2 + i) * band * 0.15f;
            float y = band * (1.2f - phase * 1.1f);
            float r = band * (0.08f + (i % 3) * 0.035f);
            int alpha = (int) (230 * (1 - phase));
            stroke.setShader(null);
            stroke.setColor(Color.argb(alpha, 210, 250, 255));
            stroke.setStrokeWidth(1.3f * dp);
            canvas.drawCircle(x, y, r, stroke);
            fill.setShader(null);
            fill.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.28f, fill);
        }
        return true;
    }

    private boolean drawGold(Canvas canvas, float w, float band, float t) {
        bandFog(canvas, w, band, Color.rgb(255, 190, 0));
        glow.setShader(new RadialGradient(w * slot(1), band * 0.55f, band * 0.8f,
                Color.argb(120, 255, 210, 80), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * slot(1), band * 0.55f, band * 0.8f, glow);
        crown(canvas, w * slot(1), band * 0.58f, band * 0.36f);
        sparkles(canvas, w, band, t, Color.rgb(255, 224, 120));
        return true;
    }

    private boolean drawSakura(Canvas canvas, float w, float band, float t) {
        bandFog(canvas, w, band, Color.rgb(255, 92, 160));
        blossom(canvas, w * slot(1), band * 0.5f, band * 0.3f);
        blossom(canvas, w * slot(2), band * 0.42f, band * 0.22f);
        for (int i = 0; i < 5; i++) {
            float phase = ((t * 0.2f) + i / 5f) % 1f;
            float x = w * slot(i % 4) + (float) Math.sin(t * 1.1 + i * 1.7) * band * 0.25f;
            petal(canvas, x, phase * band * 1.25f, band * 0.11f, t * 90 + i * 40,
                    (int) (240 * Math.sin(Math.PI * phase)));
        }
        return true;
    }

    private boolean drawPrism(Canvas canvas, float w, float band, float t) {
        bandFog(canvas, w, band, Color.rgb(139, 92, 246));
        float px = w * slot(2);
        float py = band * 0.52f;
        float s = band * 0.34f;
        int[] rainbow = {Color.rgb(255, 64, 64), Color.rgb(255, 170, 0), Color.rgb(255, 235, 59),
                Color.rgb(76, 217, 100), Color.rgb(34, 211, 238), Color.rgb(139, 92, 246)};
        stroke.setShader(null);
        stroke.setStrokeWidth(1.8f * dp);
        for (int i = 0; i < rainbow.length; i++) {
            stroke.setColor(PrismTheme.withAlpha(rainbow[i], 0.9f));
            canvas.drawLine(px - s * 0.3f, py + s * 0.2f, px - band * 1.8f, py - s + i * s * 0.42f, stroke);
        }
        path.reset();
        path.moveTo(px, py - s);
        path.lineTo(px + s, py + s * 0.8f);
        path.lineTo(px - s, py + s * 0.8f);
        path.close();
        fill.setShader(new LinearGradient(px - s, py - s, px + s, py + s,
                Color.argb(240, 255, 255, 255), Color.argb(130, 180, 200, 255), Shader.TileMode.CLAMP));
        canvas.drawPath(path, fill);
        fill.setShader(null);
        sparkles(canvas, w, band, t, Color.WHITE);
        return true;
    }

    // endregion

    // region shapes

    /** Theme-colored tint of the status bar strip, fading out just below it. */
    private void bandFog(Canvas canvas, float w, float band, int color) {
        fill.setShader(new LinearGradient(0, 0, 0, band * 1.25f,
                PrismTheme.withAlpha(color, 0.62f), PrismTheme.withAlpha(color, 0f), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, band * 1.25f, fill);
        fill.setShader(null);
    }

    /** Twinkles in the free middle-right part of the status bar. */
    private void sparkles(Canvas canvas, float w, float band, float t, int color) {
        float[][] spots = {{slot(0) + 0.02f, 0.3f}, {slot(1) - 0.02f, 0.78f}, {slot(2) + 0.025f, 0.32f},
                {slot(3) + 0.02f, 0.82f}};
        for (int i = 0; i < spots.length; i++) {
            float twinkle = (float) Math.max(0, Math.sin(t * 1.8 + i * 1.3));
            if (twinkle <= 0.05f) {
                continue;
            }
            star(canvas, spots[i][0] * w, spots[i][1] * band, band * (0.08f + 0.1f * twinkle),
                    PrismTheme.withAlpha(color, 0.9f * twinkle));
        }
    }

    private void star(Canvas canvas, float x, float y, float r, int color) {
        path.reset();
        path.moveTo(x, y - r);
        path.quadTo(x, y, x + r, y);
        path.quadTo(x, y, x, y + r);
        path.quadTo(x, y, x - r, y);
        path.quadTo(x, y, x, y - r);
        path.close();
        fill.setShader(null);
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }

    private void heart(Canvas canvas, float x, float y, float r, int color) {
        path.reset();
        path.moveTo(x, y + r);
        path.cubicTo(x - r * 2.2f, y - r * 0.2f, x - r * 0.8f, y - r * 1.6f, x, y - r * 0.5f);
        path.cubicTo(x + r * 0.8f, y - r * 1.6f, x + r * 2.2f, y - r * 0.2f, x, y + r);
        path.close();
        fill.setShader(null);
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }

    private void bow(Canvas canvas, float x, float y, float r, int color) {
        fill.setShader(null);
        fill.setColor(color);
        path.reset();
        path.moveTo(x, y);
        path.cubicTo(x - r * 0.6f, y - r * 0.9f, x - r * 1.4f, y - r * 0.7f, x - r * 1.2f, y);
        path.cubicTo(x - r * 1.4f, y + r * 0.7f, x - r * 0.6f, y + r * 0.9f, x, y);
        path.cubicTo(x + r * 0.6f, y - r * 0.9f, x + r * 1.4f, y - r * 0.7f, x + r * 1.2f, y);
        path.cubicTo(x + r * 1.4f, y + r * 0.7f, x + r * 0.6f, y + r * 0.9f, x, y);
        path.moveTo(x - r * 0.15f, y);
        path.lineTo(x - r * 0.6f, y + r * 1.3f);
        path.lineTo(x - r * 0.3f, y + r * 1.15f);
        path.close();
        path.moveTo(x + r * 0.15f, y);
        path.lineTo(x + r * 0.6f, y + r * 1.3f);
        path.lineTo(x + r * 0.3f, y + r * 1.15f);
        path.close();
        canvas.drawPath(path, fill);
        fill.setColor(PrismTheme.blend(color, Color.WHITE, 0.35f));
        canvas.drawCircle(x, y, r * 0.28f, fill);
    }

    private void crown(Canvas canvas, float x, float y, float r) {
        path.reset();
        path.moveTo(x - r, y + r * 0.5f);
        path.lineTo(x - r, y - r * 0.4f);
        path.lineTo(x - r * 0.5f, y + r * 0.05f);
        path.lineTo(x, y - r * 0.7f);
        path.lineTo(x + r * 0.5f, y + r * 0.05f);
        path.lineTo(x + r, y - r * 0.4f);
        path.lineTo(x + r, y + r * 0.5f);
        path.close();
        fill.setShader(new LinearGradient(x, y - r, x, y + r,
                Color.rgb(255, 230, 120), Color.rgb(214, 150, 0), Shader.TileMode.CLAMP));
        canvas.drawPath(path, fill);
        fill.setShader(null);
        fill.setColor(Color.rgb(230, 40, 80));
        canvas.drawCircle(x, y + r * 0.15f, r * 0.13f, fill);
        fill.setColor(Color.rgb(40, 200, 255));
        canvas.drawCircle(x - r * 0.55f, y + r * 0.25f, r * 0.1f, fill);
        canvas.drawCircle(x + r * 0.55f, y + r * 0.25f, r * 0.1f, fill);
    }

    private void blossom(Canvas canvas, float x, float y, float r) {
        fill.setShader(null);
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(i * 72 - 90);
            fill.setColor(Color.rgb(255, 183, 213));
            canvas.drawCircle(x + (float) Math.cos(angle) * r * 0.6f, y + (float) Math.sin(angle) * r * 0.6f, r * 0.5f, fill);
        }
        fill.setColor(Color.rgb(236, 72, 153));
        canvas.drawCircle(x, y, r * 0.25f, fill);
    }

    private void petal(Canvas canvas, float x, float y, float r, float rotation, int alpha) {
        canvas.save();
        canvas.rotate(rotation, x, y);
        fill.setShader(null);
        fill.setColor(Color.argb(Math.max(0, alpha), 255, 183, 213));
        oval.set(x - r, y - r * 0.55f, x + r, y + r * 0.55f);
        canvas.drawOval(oval, fill);
        canvas.restore();
    }

    // endregion
}
