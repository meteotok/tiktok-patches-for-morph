/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.graphics.Typeface;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


@SuppressWarnings("deprecation")
public final class SettingsGroupPreference extends Preference {
    private final String label;

    public SettingsGroupPreference(Context context, String label) {
        super(context);
        this.label = label;
        setTitle(label);
        setPersistent(false);
        setSelectable(false);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        Context context = getContext();
        TextView heading = SettingsUi.text(
                context,
                label,
                13f,
                SettingsUi.textSecondary(),
                Typeface.NORMAL
        );
        heading.setPadding(
                SettingsUi.dp(context, 16),
                SettingsUi.dp(context, 22),
                SettingsUi.dp(context, 16),
                SettingsUi.dp(context, 6)
        );
        heading.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        heading.setContentDescription(label + " — раздел");
        return heading;
    }
}
