package app.morphe.extension.tiktok.prism;

import android.content.res.Resources;
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
 * Theme decorations: pumpkins, a spider on its web and ghosts for Halloween, bows and hearts for
 * Femboy, petals for Sakura and so on. The layer never takes touches.
 * <p>
 * Decorations live only in the four corners of the area between the status bar and the bottom
 * bar, always at the same spots. A corner where the screen scan finds text, an input, an icon or
 * a button fades its decoration out instead of moving it somewhere else.
 */
public final class PrismDecor extends Drawable implements Runnable {
    private static final int FRAME_MS = 40;
    private static final int TAG = 0x4D455432;

    private static final int TOP_LEFT = 0;
    private static final int TOP_RIGHT = 1;
    private static final int BOTTOM_LEFT = 2;
    private static final int BOTTOM_RIGHT = 3;

    private final WeakReference<View> hostRef;
    private final float dp;
    private final float statusBar;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final RectF area = new RectF();
    private int bottomInset;
    private int topInset;
    private boolean frameQueued;
    /** Which arrangement this screen gets, so every page looks a little different. */
    private int variant;
    /** Rectangles (left, top, right, bottom) of things that must stay visible. */
    private float[] blocked = new float[0];
    /** How visible each corner's decoration is right now, fading toward free or taken. */
    private final float[] cornerAlpha = new float[4];

    private PrismDecor(View host) {
        hostRef = new WeakReference<>(host);
        Resources resources = host.getResources();
        dp = resources.getDisplayMetrics().density;
        int id = resources.getIdentifier("status_bar_height", "dimen", "android");
        statusBar = id != 0 ? resources.getDimensionPixelSize(id) : 28 * dp;
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

    void setVariant(int variant) {
        if (variant != this.variant) {
            this.variant = variant;
            invalidateSelf();
        }
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

    /** Half the size of a corner decoration, including how far it moves. */
    private float radius() {
        return 24 * dp;
    }

    /** The space a corner's decoration may use. */
    private void cornerArea(int corner, float w, float top, float bottom) {
        float size = radius() * 2;
        // Top corners sit under TikTok's top bar, bottom corners just above the bottom bar.
        float y = corner < BOTTOM_LEFT ? top + 62 * dp : bottom - size - 10 * dp;
        float x = corner % 2 == 0 ? 6 * dp : w - size - 6 * dp;
        if (corner == TOP_RIGHT && PrismTheme.current() == PrismTheme.HALLOWEEN) {
            // The spider's web is fixed to the screen edge and its thread goes down from it.
            area.set(w - size - 14 * dp, y - 8 * dp, w, y + size + 22 * dp);
        } else {
            area.set(x, y, x + size, y + size);
        }
    }

    private boolean isFree(RectF rect) {
        float pad = 6 * dp;
        float left = rect.left - pad;
        float top = rect.top - pad;
        float right = rect.right + pad;
        float bottom = rect.bottom + pad;
        for (int i = 0; i + 3 < blocked.length; i += 4) {
            if (left < blocked[i + 2] && blocked[i] < right && top < blocked[i + 3] && blocked[i + 1] < bottom) {
                return false;
            }
        }
        return true;
    }

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
        if (w <= 0 || bottom - top < radius() * 8) {
            return;
        }
        float t = (SystemClock.uptimeMillis() % 600_000L) / 1000f;
        boolean moving = false;
        canvas.save();
        canvas.clipRect(0, top, w, bottom);
        try {
            for (int corner = 0; corner < 4; corner++) {
                cornerArea(corner, w, top, bottom);
                float target = isFree(area) ? 1f : 0f;
                float alpha = cornerAlpha[corner] + (target - cornerAlpha[corner]) * 0.18f;
                if (Math.abs(alpha - target) < 0.02f) {
                    alpha = target;
                }
                cornerAlpha[corner] = alpha;
                if (alpha != target) {
                    moving = true;
                }
                if (alpha <= 0f) {
                    continue;
                }
                moving = true;
                int layer = alpha < 1f
                        ? canvas.saveLayerAlpha(area.left - radius(), area.top - radius(), area.right + radius(),
                        area.bottom + radius(), (int) (255 * alpha))
                        : canvas.save();
                drawCorner(canvas, corner, w, t);
                canvas.restoreToCount(layer);
            }
        } catch (Throwable ignored) {
            // Decorations must never break TikTok's drawing.
        }
        canvas.restore();
        View host = hostRef.get();
        if (moving && !frameQueued && host != null && host.isShown()) {
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

    /** One decoration in its corner, which is in {@link #area}. */
    private void drawCorner(Canvas canvas, int corner, float w, float t) {
        float x = area.centerX();
        float y = area.centerY();
        int index = corner + variant;
        float bob = (float) Math.sin(t * 1.3 + index) * 3 * dp;
        float flicker = 0.75f + 0.25f * (float) Math.abs(Math.sin(t * 6.3 + index) * Math.sin(t * 2.1));
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 2.4 + index);
        switch (PrismTheme.current()) {
            case HALLOWEEN:
                if (corner == TOP_RIGHT) {
                    hangingSpider(canvas, w, t);
                    break;
                }
                // The other three corners take turns between screens.
                int kind = Math.floorMod(corner + variant, 3);
                if (kind == 0) {
                    pumpkin(canvas, x, y + 3 * dp, 18 * dp, flicker);
                } else if (kind == 1) {
                    ghost(canvas, x, y + bob, 14 * dp);
                } else {
                    candyBucket(canvas, x, y + 9 * dp, 13 * dp);
                }
                break;
            case FEMBOY:
                if (index % 2 == 0) {
                    bow(canvas, x, y + bob - 4 * dp, 15 * dp,
                            corner < BOTTOM_LEFT ? Color.rgb(255, 92, 170) : Color.rgb(120, 190, 255));
                } else {
                    risingHearts(canvas, x, y, t + index);
                }
                break;
            case NEON:
                bolt(canvas, x, y, 42 * dp, pulse, index % 2 == 0 ? Color.rgb(57, 255, 20) : Color.rgb(255, 0, 229));
                break;
            case OCEAN:
                bubbles(canvas, x, y, t + index * 0.37f);
                break;
            case GOLD:
                glow.setShader(new RadialGradient(x, y, 22 * dp, Color.argb((int) (130 * pulse), 255, 210, 80),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(x, y, 22 * dp, glow);
                crown(canvas, x, y + bob, 15 * dp);
                break;
            case SAKURA:
                blossom(canvas, x, y + bob * 0.5f, index % 2 == 0 ? 16 * dp : 13 * dp);
                float phase = ((t * 0.3f) + index * 0.29f) % 1f;
                petal(canvas, x + (float) Math.sin(t * 1.2 + index) * 10 * dp, y - 18 * dp + phase * 36 * dp,
                        6 * dp, t * 90 + index * 40, (int) (240 * Math.sin(Math.PI * phase)));
                break;
            case PRISM:
                int[] colors = {Color.rgb(255, 64, 64), Color.rgb(255, 235, 59), Color.rgb(34, 211, 238),
                        Color.rgb(139, 92, 246)};
                for (int i = 0; i < 3; i++) {
                    float twinkle = (float) Math.max(0.15, Math.sin(t * 1.8 + index + i * 2.1));
                    star(canvas, x + (i - 1) * 12 * dp, y + (i == 1 ? -9 : 8) * dp, (6 + 8 * twinkle) * dp,
                            PrismTheme.withAlpha(colors[(index + i) % colors.length], 0.95f * twinkle));
                }
                break;
            default:
                break;
        }
    }

    /**
     * A small web on the right edge with the spider on its thread, slowly letting itself down
     * and climbing back up, swinging a little.
     */
    private void hangingSpider(Canvas canvas, float w, float t) {
        float anchor = area.top;
        cobweb(canvas, w, anchor, 30 * dp, -1);
        float x = w - 22 * dp;
        float length = 16 * dp + (area.height() - 46 * dp) * (0.5f + 0.5f * (float) Math.sin(t * 0.6));
        float swing = (float) Math.sin(t * 1.7) * 7f;
        canvas.save();
        canvas.rotate(swing, x, anchor);
        stroke.setShader(null);
        stroke.setColor(Color.argb(210, 255, 255, 255));
        stroke.setStrokeWidth(1f * dp);
        canvas.drawLine(x, anchor, x, anchor + length, stroke);
        spider(canvas, x, anchor + length + 9 * dp, 0.8f);
        canvas.restore();
    }

    // region shapes

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

    private void risingHearts(Canvas canvas, float x, float y, float t) {
        for (int i = 0; i < 3; i++) {
            float phase = ((t * 0.3f) + i / 3f) % 1f;
            heart(canvas, x + (i - 1) * 12 * dp + (float) Math.sin(t * 2 + i) * 3 * dp, y + 18 * dp - phase * 36 * dp,
                    (6 + i % 2 * 2) * dp, PrismTheme.withAlpha(i == 1 ? Color.rgb(120, 190, 255) : Color.rgb(255, 92, 170),
                            (float) Math.sin(Math.PI * phase)));
        }
    }

    private void bubbles(Canvas canvas, float x, float y, float t) {
        for (int i = 0; i < 4; i++) {
            float phase = ((t * (0.25f + i * 0.04f)) + i / 4f) % 1f;
            float bx = x + (float) Math.sin(t * 2 + i * 1.7) * 9 * dp;
            float by = y + 22 * dp - phase * 44 * dp;
            float r = (4 + (i % 3) * 2.5f) * dp;
            int alpha = (int) (240 * Math.sin(Math.PI * phase));
            stroke.setShader(null);
            stroke.setColor(Color.argb(alpha, 210, 250, 255));
            stroke.setStrokeWidth(1.4f * dp);
            canvas.drawCircle(bx, by, r, stroke);
            fill.setShader(null);
            fill.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(bx - r * 0.35f, by - r * 0.35f, r * 0.28f, fill);
        }
    }

    /** Lightning bolt about {@code size} tall, centered on (x, y), with a glow of {@code color}. */
    private void bolt(Canvas canvas, float x, float y, float size, float pulse, int color) {
        float top = y - size * 0.4f;
        path.reset();
        path.moveTo(x + size * 0.1f, top);
        path.lineTo(x - size * 0.18f, top + size * 0.43f);
        path.lineTo(x + size * 0.02f, top + size * 0.43f);
        path.lineTo(x - size * 0.08f, top + size * 0.8f);
        path.lineTo(x + size * 0.22f, top + size * 0.3f);
        path.lineTo(x + size * 0.02f, top + size * 0.3f);
        path.close();
        glow.setShader(new RadialGradient(x, y, size * 0.6f,
                PrismTheme.withAlpha(color, 0.45f * pulse), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, size * 0.6f, glow);
        fill.setShader(null);
        fill.setColor(PrismTheme.blend(color, Color.WHITE, 0.7f));
        canvas.drawPath(path, fill);
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
