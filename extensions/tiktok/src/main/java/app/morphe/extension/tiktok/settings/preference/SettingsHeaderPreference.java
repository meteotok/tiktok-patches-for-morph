/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public final class SettingsHeaderPreference extends Preference {
    private enum Kind {
        MASTER,
        SECTION,
        CAPTION
    }

    private final Kind kind;
    private final String heading;
    private final String detail;
    private final Runnable backAction;

    public static SettingsHeaderPreference master(Context context, Runnable backAction) {
        return new SettingsHeaderPreference(
                context,
                Kind.MASTER,
                "Prism",
                null,
                backAction
        );
    }

    public static SettingsHeaderPreference section(Context context, String title, Runnable backAction) {
        return new SettingsHeaderPreference(context, Kind.SECTION, title, null, backAction);
    }

    public static SettingsHeaderPreference caption(Context context, String caption) {
        return new SettingsHeaderPreference(context, Kind.CAPTION, null, caption, null);
    }

    private SettingsHeaderPreference(
            Context context,
            Kind kind,
            String heading,
            String detail,
            Runnable backAction
    ) {
        super(context);
        this.kind = kind;
        this.heading = heading;
        this.detail = detail;
        this.backAction = backAction;
        setSelectable(kind != Kind.CAPTION);
        setOrder(kind == Kind.CAPTION ? -900 : -1000);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        if (kind == Kind.MASTER) {
            return createMasterHeader();
        }
        if (kind == Kind.CAPTION) {
            return createCaption();
        }
        return createSectionHeader();
    }

    private View createMasterHeader() {
        return createTopBar();
    }

    private View createSectionHeader() {
        return createTopBar();
    }

    /** Top bar like TikTok's settings screens: back arrow and a centered bold title. */
    private View createTopBar() {
        Context context = getContext();
        android.widget.FrameLayout bar = new android.widget.FrameLayout(context);
        bar.setMinimumHeight(SettingsUi.dp(context, 52));
        bar.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        ImageView back = new ImageView(context);
        back.setContentDescription("Назад");
        back.setImageDrawable(new BackDrawable());
        back.setOnClickListener(view -> {
            if (backAction != null) {
                backAction.run();
            }
        });
        int buttonSize = SettingsUi.dp(context, 48);
        android.widget.FrameLayout.LayoutParams backParams =
                new android.widget.FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.START | Gravity.CENTER_VERTICAL);
        backParams.leftMargin = SettingsUi.dp(context, 4);
        bar.addView(back, backParams);

        TextView title = SettingsUi.text(context, heading, 17, SettingsUi.textPrimary(), 1);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        android.widget.FrameLayout.LayoutParams titleParams =
                new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        titleParams.leftMargin = SettingsUi.dp(context, 56);
        titleParams.rightMargin = SettingsUi.dp(context, 56);
        bar.addView(title, titleParams);
        return bar;
    }

    private View createSupportPill(Context context) {
        return SupportUi.createPill(context);
    }

    private View createCaption() {
        Context context = getContext();
        TextView caption = SettingsUi.text(context, detail, 13.5f, SettingsUi.textSecondary(), 0);
        caption.setPadding(
                SettingsUi.dp(context, 22),
                SettingsUi.dp(context, 12),
                SettingsUi.dp(context, 22),
                SettingsUi.dp(context, 8)
        );
        caption.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        return caption;
    }

    private static final class BackDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BackDrawable() {
            paint.setColor(SettingsUi.textPrimary());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.1f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            float centerX = getBounds().exactCenterX();
            float centerY = getBounds().exactCenterY();
            float offset = Math.min(getBounds().width(), getBounds().height()) * 0.18f;
            float pointX = centerX - offset;
            canvas.drawLine(pointX, centerY, centerX + offset, centerY, paint);
            canvas.drawLine(pointX, centerY, centerX - offset * 0.1f, centerY - offset * 0.9f, paint);
            canvas.drawLine(pointX, centerY, centerX - offset * 0.1f, centerY + offset * 0.9f, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
