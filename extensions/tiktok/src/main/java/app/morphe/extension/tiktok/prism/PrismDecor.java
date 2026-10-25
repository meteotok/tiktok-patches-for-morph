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
        float ground = h - Math.max(bottomInset, 0);
        boolean animated;
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

    private boolean drawHalloween(Canvas canvas, float w, float ground, float t) {
        edgeFog(canvas, w, ground, Color.rgb(142, 45, 226), Color.rgb(255, 122, 24));

        cobweb(canvas, 0, 0, 92 * dp, 1);
        cobweb(canvas, w, 0, 78 * dp, -1);

        // A spider dangling from the right web, swinging and bobbing.
        float swing = (float) Math.sin(t * 1.6) * 7f;
        float length = 120 * dp + (float) Math.sin(t * 0.7) * 14 * dp;
        canvas.save();
        canvas.rotate(swing, w - 46 * dp, 34 * dp);
        stroke.setShader(null);
        stroke.setColor(Color.argb(170, 255, 255, 255));
        stroke.setStrokeWidth(1f * dp);
        canvas.drawLine(w - 46 * dp, 34 * dp, w - 46 * dp, 34 * dp + length, stroke);
        spider(canvas, w - 46 * dp, 34 * dp + length + 9 * dp, 1f);
        canvas.restore();

        // A smaller one on the left web, still.
        spider(canvas, 54 * dp, 46 * dp, 0.6f);

        // Bats flapping across the top.
        float flap = (float) Math.sin(t * 9);
        bat(canvas, w * 0.30f + (float) Math.sin(t * 0.5) * 18 * dp, 70 * dp + (float) Math.cos(t * 0.8) * 6 * dp, 13 * dp, flap);
        bat(canvas, w * 0.62f + (float) Math.cos(t * 0.4) * 22 * dp, 96 * dp + (float) Math.sin(t * 0.9) * 5 * dp, 9 * dp, -flap);

        // Jack-o'-lantern and the candy bucket sitting on the bottom bar.
        float flicker = 0.75f + 0.25f * (float) Math.abs(Math.sin(t * 6.3) * Math.sin(t * 2.1));
        pumpkin(canvas, 34 * dp, ground - 22 * dp, 25 * dp, flicker);
        candyBucket(canvas, w - 40 * dp, ground - 24 * dp, 24 * dp);
        return true;
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

    private boolean drawFemboy(Canvas canvas, float w, float ground, float t) {
        edgeFog(canvas, w, ground, Color.rgb(255, 111, 181), Color.rgb(126, 200, 255));
        bow(canvas, 36 * dp, 40 * dp, 18 * dp, Color.rgb(255, 111, 181));
        bow(canvas, w - 36 * dp, 40 * dp, 14 * dp, Color.rgb(126, 200, 255));
        // Hearts floating up along both edges.
        for (int i = 0; i < 6; i++) {
            float phase = ((t * 0.12f) + i / 6f) % 1f;
            float x = (i % 2 == 0 ? 22 * dp : w - 22 * dp) + (float) Math.sin(t * 1.3 + i) * 8 * dp;
            float y = ground - 30 * dp - phase * ground * 0.55f;
            int alpha = (int) (220 * Math.sin(Math.PI * phase));
            heart(canvas, x, y, (7 + (i % 3) * 2) * dp,
                    PrismTheme.withAlpha(i % 3 == 0 ? Color.rgb(126, 200, 255) : Color.rgb(255, 111, 181), alpha / 255f));
        }
        sparkles(canvas, w, ground, t, Color.WHITE);
        return true;
    }

    private boolean drawNeon(Canvas canvas, float w, float ground, float t) {
        float pulse = 0.65f + 0.35f * (float) Math.sin(t * 2.4);
        cornerBrackets(canvas, w, ground, Color.rgb(57, 255, 20), Color.rgb(255, 0, 229), pulse);
        sparkles(canvas, w, ground, t, Color.rgb(57, 255, 20));
        return true;
    }

    private boolean drawOcean(Canvas canvas, float w, float ground, float t) {
        edgeFog(canvas, w, ground, Color.rgb(0, 194, 255), Color.rgb(0, 255, 163));
        for (int i = 0; i < 10; i++) {
            float phase = ((t * (0.08f + (i % 3) * 0.02f)) + i / 10f) % 1f;
            float x = (i % 2 == 0 ? 16 * dp + (i % 4) * 6 * dp : w - 16 * dp - (i % 4) * 6 * dp)
                    + (float) Math.sin(t * 2 + i) * 5 * dp;
            float y = ground - 10 * dp - phase * ground * 0.7f;
            float r = (3 + (i % 4) * 1.6f) * dp;
            stroke.setShader(null);
            stroke.setColor(Color.argb((int) (190 * (1 - phase)), 200, 245, 255));
            stroke.setStrokeWidth(1.2f * dp);
            canvas.drawCircle(x, y, r, stroke);
            fill.setShader(null);
            fill.setColor(Color.argb((int) (160 * (1 - phase)), 255, 255, 255));
            canvas.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.25f, fill);
        }
        seaweed(canvas, 14 * dp, ground, t, 1);
        seaweed(canvas, w - 14 * dp, ground, t, -1);
        return true;
    }

    private boolean drawGold(Canvas canvas, float w, float ground, float t) {
        edgeFog(canvas, w, ground, Color.rgb(255, 196, 0), Color.rgb(255, 120, 40));
        crown(canvas, w - 40 * dp, ground - 18 * dp, 20 * dp);
        sparkles(canvas, w, ground, t, Color.rgb(255, 214, 90));
        return true;
    }

    private boolean drawSakura(Canvas canvas, float w, float ground, float t) {
        // Branch with blossoms in the top left corner.
        stroke.setShader(null);
        stroke.setColor(Color.rgb(92, 58, 48));
        stroke.setStrokeWidth(5 * dp);
        path.reset();
        path.moveTo(-4 * dp, 26 * dp);
        path.cubicTo(40 * dp, 34 * dp, 70 * dp, 22 * dp, 118 * dp, 46 * dp);
        canvas.drawPath(path, stroke);
        stroke.setStrokeWidth(3 * dp);
        canvas.drawLine(60 * dp, 30 * dp, 84 * dp, 68 * dp, stroke);
        blossom(canvas, 40 * dp, 26 * dp, 9 * dp);
        blossom(canvas, 82 * dp, 70 * dp, 8 * dp);
        blossom(canvas, 116 * dp, 46 * dp, 10 * dp);
        blossom(canvas, 72 * dp, 22 * dp, 7 * dp);
        // Falling petals.
        for (int i = 0; i < 9; i++) {
            float phase = ((t * (0.05f + (i % 3) * 0.015f)) + i / 9f) % 1f;
            float x = (i * 0.11f + 0.04f) * w + (float) Math.sin(t * 1.1 + i * 1.7) * 26 * dp;
            float y = phase * ground;
            petal(canvas, x, y, 5.5f * dp, t * 90 + i * 40, (int) (230 * Math.sin(Math.PI * phase)));
        }
        return true;
    }

    private boolean drawPrism(Canvas canvas, float w, float ground, float t) {
        edgeFog(canvas, w, ground, Color.rgb(139, 92, 246), Color.rgb(34, 211, 238));
        // A little glass prism splitting light into a rainbow.
        float px = w - 34 * dp;
        float py = 40 * dp;
        float s = 15 * dp;
        int[] rainbow = {Color.rgb(255, 64, 64), Color.rgb(255, 170, 0), Color.rgb(255, 235, 59),
                Color.rgb(76, 217, 100), Color.rgb(34, 211, 238), Color.rgb(139, 92, 246)};
        stroke.setShader(null);
        stroke.setStrokeWidth(2.2f * dp);
        for (int i = 0; i < rainbow.length; i++) {
            stroke.setColor(PrismTheme.withAlpha(rainbow[i], 0.75f));
            canvas.drawLine(px - s * 0.2f, py + s * 0.3f, px - 70 * dp, py + 30 * dp + i * 5 * dp, stroke);
        }
        path.reset();
        path.moveTo(px, py - s);
        path.lineTo(px + s, py + s * 0.8f);
        path.lineTo(px - s, py + s * 0.8f);
        path.close();
        fill.setShader(new LinearGradient(px - s, py - s, px + s, py + s,
                Color.argb(200, 255, 255, 255), Color.argb(90, 180, 200, 255), Shader.TileMode.CLAMP));
        canvas.drawPath(path, fill);
        stroke.setColor(Color.argb(230, 255, 255, 255));
        stroke.setStrokeWidth(1.2f * dp);
        canvas.drawPath(path, stroke);
        sparkles(canvas, w, ground, t, Color.WHITE);
        return true;
    }

    // endregion

    // region shapes

    /** Soft colored mist at the top and above the bottom bar. */
    private void edgeFog(Canvas canvas, float w, float ground, int top, int bottom) {
        float band = 120 * dp;
        fill.setShader(new LinearGradient(0, 0, 0, band,
                PrismTheme.withAlpha(top, 0.30f), PrismTheme.withAlpha(top, 0f), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, band, fill);
        fill.setShader(new LinearGradient(0, ground - band, 0, ground,
                PrismTheme.withAlpha(bottom, 0f), PrismTheme.withAlpha(bottom, 0.26f), Shader.TileMode.CLAMP));
        canvas.drawRect(0, ground - band, w, ground, fill);
        fill.setShader(null);
    }

    private void sparkles(Canvas canvas, float w, float ground, float t, int color) {
        float[][] spots = {{0.08f, 0.16f}, {0.92f, 0.22f}, {0.06f, 0.62f}, {0.94f, 0.48f}, {0.5f, 0.07f}, {0.88f, 0.8f}};
        for (int i = 0; i < spots.length; i++) {
            float twinkle = (float) Math.max(0, Math.sin(t * 1.8 + i * 1.3));
            if (twinkle <= 0.05f) {
                continue;
            }
            star(canvas, spots[i][0] * w, spots[i][1] * ground, (4 + 4 * twinkle) * dp,
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

    private void cornerBrackets(Canvas canvas, float w, float ground, int a, int b, float pulse) {
        float len = 46 * dp;
        float inset = 10 * dp;
        for (int layer = 3; layer >= 0; layer--) {
            stroke.setShader(null);
            stroke.setStrokeWidth((2 + layer * 3) * dp);
            float alpha = layer == 0 ? 1f : 0.16f * pulse;
            stroke.setColor(PrismTheme.withAlpha(a, alpha));
            path.reset();
            path.moveTo(inset, inset + len);
            path.lineTo(inset, inset);
            path.lineTo(inset + len, inset);
            path.moveTo(w - inset, ground - inset - len);
            path.lineTo(w - inset, ground - inset);
            path.lineTo(w - inset - len, ground - inset);
            canvas.drawPath(path, stroke);
            stroke.setColor(PrismTheme.withAlpha(b, alpha));
            path.reset();
            path.moveTo(w - inset - len, inset);
            path.lineTo(w - inset, inset);
            path.lineTo(w - inset, inset + len);
            path.moveTo(inset + len, ground - inset);
            path.lineTo(inset, ground - inset);
            path.lineTo(inset, ground - inset - len);
            canvas.drawPath(path, stroke);
        }
    }

    private void seaweed(Canvas canvas, float x, float ground, float t, int direction) {
        stroke.setShader(null);
        stroke.setStrokeWidth(4 * dp);
        for (int i = 0; i < 3; i++) {
            float sway = (float) Math.sin(t * 1.2 + i) * 8 * dp;
            float bx = x + direction * i * 7 * dp;
            float height = (40 + i * 14) * dp;
            stroke.setColor(PrismTheme.withAlpha(Color.rgb(20, 180, 120), 0.75f));
            path.reset();
            path.moveTo(bx, ground);
            path.cubicTo(bx + sway, ground - height * 0.35f, bx - sway, ground - height * 0.7f,
                    bx + sway * 0.6f, ground - height);
            canvas.drawPath(path, stroke);
        }
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
