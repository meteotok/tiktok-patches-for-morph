package app.morphe.extension.tiktok.prism;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

/** Theme picker, app icon picker and the password protected Admin Panel. */
@SuppressWarnings("deprecation")
public final class PrismDialogs {
    // PBKDF2-HMAC-SHA1 of the admin password. Only the hash ships in the APK.
    private static final String ADMIN_SALT = "prism-admin-v1";
    private static final int ADMIN_ITERATIONS = 20000;
    private static final String ADMIN_HASH = "2d93c84accdd56cd51472f43eef31f638ec093840eeab8f90f5524bde2dcc875";

    private static boolean adminUnlocked;

    private PrismDialogs() {
    }

    // region shared building blocks

    private static int dp(Context context, int value) {
        return SettingsUi.dp(context, value);
    }

    private static TextView title(Context context, String text) {
        TextView view = SettingsUi.text(context, text, 21, SettingsUi.textPrimary(), Typeface.BOLD);
        view.setPadding(dp(context, 22), dp(context, 20), dp(context, 22), dp(context, 4));
        return view;
    }

    private static TextView caption(Context context, String text) {
        TextView view = SettingsUi.text(context, text, 13, SettingsUi.textSecondary(), Typeface.NORMAL);
        view.setPadding(dp(context, 22), 0, dp(context, 22), dp(context, 12));
        return view;
    }

    private static TextView button(Context context, String text, boolean primary) {
        TextView view = SettingsUi.text(context, text, 14.5f,
                primary ? Color.WHITE : SettingsUi.textPrimary(), Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        GradientDrawable background;
        if (primary) {
            background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{SettingsUi.ACCENT, SettingsUi.ACCENT_2});
        } else {
            background = SettingsUi.glassCard(context, 16);
        }
        background.setCornerRadius(dp(context, 16));
        view.setBackground(background);
        return view;
    }

    private static AlertDialog show(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).create();
        dialog.show();
        SettingsUi.styleFramedDialog(dialog);
        return dialog;
    }

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, 0, dp(context, 14));
        return layout;
    }

    private static LinearLayout.LayoutParams rowParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(context, 14);
        params.rightMargin = dp(context, 14);
        params.topMargin = dp(context, 4);
        params.bottomMargin = dp(context, 4);
        return params;
    }

    /** Circle swatch with the two theme colors. */
    private static final class SwatchDrawable extends Drawable {
        private final PrismTheme theme;
        private final boolean selected;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwatchDrawable(PrismTheme theme, boolean selected) {
            this.theme = theme;
            this.selected = selected;
            ring.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float r = Math.min(bounds.width(), bounds.height()) / 2f;
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    theme.backgroundTop(), theme.backgroundBottom(), Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 0.96f, paint);
            paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    theme.accent, theme.accent2, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 0.56f, paint);
            ring.setStrokeWidth(r * (selected ? 0.12f : 0.05f));
            ring.setColor(selected ? theme.accent : Color.argb(90, 128, 128, 128));
            canvas.drawCircle(cx, cy, r * 0.94f, ring);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // endregion

    // region theme picker

    public static void showThemePicker(Activity activity) {
        Context context = activity;
        LinearLayout content = column(context);
        content.addView(title(context, "Темы"));
        content.addView(caption(context, "Красит меню мода, стеклянную нижнюю панель и окна."));
        AlertDialog[] holder = new AlertDialog[1];
        PrismTheme selected = PrismTheme.current();

        for (PrismTheme theme : PrismTheme.values()) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
            GradientDrawable card = SettingsUi.glassCard(context, 18);
            if (theme == selected) {
                card.setStroke(dp(context, 2), theme.accent);
            }
            row.setBackground(card);

            ImageView swatch = new ImageView(context);
            swatch.setImageDrawable(new SwatchDrawable(theme, theme == selected));
            row.addView(swatch, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(SettingsUi.text(context, theme.title, 15.5f, SettingsUi.textPrimary(), Typeface.BOLD));
            labels.addView(SettingsUi.text(context, theme.description, 12.5f, SettingsUi.textSecondary(), Typeface.NORMAL));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.leftMargin = dp(context, 14);
            row.addView(labels, labelParams);

            row.setOnClickListener(view -> {
                Settings.PRISM_THEME.save(theme.key);
                SettingsUi.refreshTheme();
                PrismUi.styleChanged();
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                activity.recreate();
            });
            content.addView(row, rowParams(context));
        }
        holder[0] = show(context, content);
    }

    // endregion

    // region app icon picker

    public static void showIconPicker(Activity activity) {
        Context context = activity;
        if (!PrismIcons.available(context)) {
            Utils.showToastLong("Иконки не встроены: пересобери APK с патчем «App icons»");
            return;
        }
        LinearLayout content = column(context);
        content.addView(title(context, "Иконка приложения"));
        content.addView(caption(context, "Лаунчер обновит иконку через пару секунд. "
                + "Ярлык на рабочем столе, возможно, придётся добавить заново."));
        AlertDialog[] holder = new AlertDialog[1];
        PrismIcons selected = PrismIcons.current();

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(4);
        grid.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        int cell = dp(context, 74);
        for (PrismIcons icon : PrismIcons.values()) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 8));
            if (icon == selected) {
                GradientDrawable card = SettingsUi.glassCard(context, 18);
                card.setStroke(dp(context, 2), SettingsUi.ACCENT);
                item.setBackground(card);
            }

            ImageView preview = new ImageView(context);
            Drawable drawable = icon.preview(context);
            if (drawable != null) {
                preview.setImageDrawable(drawable);
            }
            item.addView(preview, new LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)));

            TextView label = SettingsUi.text(context, icon.title, 11.5f, SettingsUi.textPrimary(),
                    icon == selected ? Typeface.BOLD : Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            item.addView(label, new LinearLayout.LayoutParams(-1, -2));

            item.setOnClickListener(view -> {
                if (icon.apply(context)) {
                    Utils.showToastShort("Иконка: " + icon.title);
                    PrismUi.styleChanged();
                }
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cell;
            params.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
            grid.addView(item, params);
        }
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-2, -2);
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(grid, gridParams);
        holder[0] = show(context, content);
    }

    // endregion

    // region admin panel

    static boolean checkPassword(String password) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                    ADMIN_SALT.getBytes(java.nio.charset.StandardCharsets.UTF_8), ADMIN_ITERATIONS, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return ADMIN_HASH.contentEquals(hex);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void openAdminPanel(Activity activity) {
        if (adminUnlocked) {
            showAdminPanel(activity);
            return;
        }
        Context context = activity;
        LinearLayout content = column(context);
        content.addView(title(context, "Admin Panel"));
        content.addView(caption(context, "Введи пароль администратора."));

        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Пароль");
        input.setSingleLine(true);
        SettingsUi.styleEditText(input);
        content.addView(input, rowParams(context));

        TextView enter = button(context, "Войти", true);
        content.addView(enter, rowParams(context));
        AlertDialog dialog = show(context, content);
        enter.setOnClickListener(view -> {
            if (checkPassword(input.getText().toString())) {
                adminUnlocked = true;
                dialog.dismiss();
                showAdminPanel(activity);
            } else {
                input.setText("");
                input.setError("Неверный пароль");
            }
        });
    }

    private static void showAdminPanel(Activity activity) {
        Context context = activity;
        LinearLayout content = column(context);
        content.addView(title(context, "Admin Panel"));
        content.addView(caption(context, "Бейджи у ников. Локальные видишь только ты; "
                + "чтобы их увидели все с этим модом, скопируй список в файл "
                + "prism-badges.txt в репозитории."));

        // Preview of every badge style in the colors of the current app icon.
        LinearLayout previews = new LinearLayout(context);
        previews.setGravity(Gravity.CENTER);
        for (PrismBadges.Type type : PrismBadges.Type.values()) {
            ImageView badge = new ImageView(context);
            badge.setImageDrawable(new PrismBadges.BadgeDrawable(type));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 30), dp(context, 30));
            params.leftMargin = dp(context, 5);
            params.rightMargin = dp(context, 5);
            previews.addView(badge, params);
        }
        content.addView(previews, rowParams(context));

        AlertDialog[] holder = new AlertDialog[1];
        Map<String, PrismBadges.Type> badges = PrismBadges.localBadges();
        if (badges.isEmpty()) {
            TextView empty = caption(context, "Пока никому не выдано.");
            empty.setGravity(Gravity.CENTER);
            content.addView(empty);
        }
        for (Map.Entry<String, PrismBadges.Type> entry : badges.entrySet()) {
            content.addView(badgeRow(activity, entry.getKey(), entry.getValue(), holder), rowParams(context));
        }

        int remoteCount = PrismBadges.remoteBadges().size();
        content.addView(caption(context, "Из репозитория загружено: " + remoteCount));

        TextView grant = button(context, "Выдать бейдж", true);
        grant.setOnClickListener(view -> {
            holder[0].dismiss();
            showGrantDialog(activity);
        });
        content.addView(grant, rowParams(context));

        TextView copy = button(context, "Скопировать список для репозитория", false);
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("prism-badges",
                        PrismBadges.serialize(PrismBadges.localBadges())));
                Utils.showToastShort("Скопировано");
            }
        });
        content.addView(copy, rowParams(context));

        TextView refresh = button(context, "Обновить из репозитория", false);
        refresh.setOnClickListener(view -> {
            PrismBadges.refreshRemote(true);
            Utils.showToastShort("Обновляю список…");
        });
        content.addView(refresh, rowParams(context));

        TextView lock = button(context, "Выйти из админки", false);
        lock.setOnClickListener(view -> {
            adminUnlocked = false;
            holder[0].dismiss();
        });
        content.addView(lock, rowParams(context));

        holder[0] = show(context, content);
    }

    private static View badgeRow(Activity activity, String nickname, PrismBadges.Type type, AlertDialog[] holder) {
        Context context = activity;
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 8), dp(context, 8), dp(context, 8));
        row.setBackground(SettingsUi.glassCard(context, 16));

        ImageView badge = new ImageView(context);
        badge.setImageDrawable(new PrismBadges.BadgeDrawable(type));
        row.addView(badge, new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(SettingsUi.text(context, "@" + nickname, 15, SettingsUi.textPrimary(), Typeface.BOLD));
        labels.addView(SettingsUi.text(context, type.title, 12, SettingsUi.textSecondary(), Typeface.NORMAL));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(context, 12);
        row.addView(labels, labelParams);

        TextView remove = SettingsUi.text(context, "Забрать", 13, SettingsUi.ACCENT, Typeface.BOLD);
        remove.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        remove.setOnClickListener(view -> {
            PrismBadges.remove(nickname);
            holder[0].dismiss();
            showAdminPanel(activity);
        });
        row.addView(remove);
        return row;
    }

    private static void showGrantDialog(Activity activity) {
        Context context = activity;
        LinearLayout content = column(context);
        content.addView(title(context, "Выдать бейдж"));
        content.addView(caption(context, "Ник или @username — ровно как он написан в TikTok."));

        EditText nickname = new EditText(context);
        nickname.setHint("@username");
        nickname.setSingleLine(true);
        nickname.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        SettingsUi.styleEditText(nickname);
        content.addView(nickname, rowParams(context));

        PrismBadges.Type[] chosen = {PrismBadges.Type.VERIFIED};
        LinearLayout types = new LinearLayout(context);
        types.setGravity(Gravity.CENTER);
        FrameLayout[] cells = new FrameLayout[PrismBadges.Type.values().length];
        TextView typeName = caption(context, chosen[0].title);
        typeName.setGravity(Gravity.CENTER);
        for (int i = 0; i < cells.length; i++) {
            PrismBadges.Type type = PrismBadges.Type.values()[i];
            FrameLayout cell = new FrameLayout(context);
            cell.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
            ImageView badge = new ImageView(context);
            badge.setImageDrawable(new PrismBadges.BadgeDrawable(type));
            cell.addView(badge, new FrameLayout.LayoutParams(dp(context, 32), dp(context, 32)));
            cells[i] = cell;
            cell.setOnClickListener(view -> {
                chosen[0] = type;
                typeName.setText(type.title);
                for (FrameLayout other : cells) {
                    other.setBackground(null);
                }
                GradientDrawable ring = SettingsUi.glassCard(context, 14);
                ring.setStroke(dp(context, 2), SettingsUi.ACCENT);
                cell.setBackground(ring);
            });
            types.addView(cell);
        }
        GradientDrawable ring = SettingsUi.glassCard(context, 14);
        ring.setStroke(dp(context, 2), SettingsUi.ACCENT);
        cells[0].setBackground(ring);
        content.addView(types, rowParams(context));
        content.addView(typeName);

        TextView save = button(context, "Выдать", true);
        content.addView(save, rowParams(context));
        AlertDialog dialog = show(context, content);
        save.setOnClickListener(view -> {
            String value = PrismBadges.normalize(nickname.getText());
            if (value.isEmpty()) {
                nickname.setError("Пустой ник");
                return;
            }
            PrismBadges.put(value, chosen[0]);
            Utils.showToastShort("Бейдж выдан @" + value);
            dialog.dismiss();
            showAdminPanel(activity);
        });
    }

    // endregion

    /** Small helper so callers can find the hosting activity of a preference. */
    @Nullable
    public static Activity activityOf(@Nullable Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
