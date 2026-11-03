package app.morphe.extension.tiktok.settings.preference;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.widget.Toast;

import app.morphe.extension.tiktok.profile.ProfileDebug;

@SuppressWarnings("deprecation")
public final class ProfileDebugPreference extends Preference {
    public ProfileDebugPreference(Context context) {
        super(context);
        setTitle("Скопировать отладку профиля");
        setSummary("Открой свой профиль, вернись сюда, нажми и пришли скопированный текст.");
        setOnPreferenceClickListener(preference -> {
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Prisma profile debug", ProfileDebug.get()));
                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(context, "Не вышло: " + t, Toast.LENGTH_LONG).show();
            }
            return true;
        });
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }
}
