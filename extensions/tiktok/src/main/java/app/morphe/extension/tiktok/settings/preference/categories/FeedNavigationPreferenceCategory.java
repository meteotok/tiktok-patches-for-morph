package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TabSelectionPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class FeedNavigationPreferenceCategory extends ConditionalPreferenceCategory {
    public FeedNavigationPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Вкладки");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.feedNavigationEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(group(context, "Вкладки ленты"));
        addPreference(new TogglePreference(
                context,
                "Фильтровать вкладки ленты",
                "Какие вкладки ленты TikTok оставить.",
                Settings.FEED_NAVIGATION
        ));
        addPreference(new TabSelectionPreference(
                context,
                Settings.FEED_NAVIGATION_TABS
        ));
        addPreference(new TogglePreference(
                context,
                "Блокировать новые вкладки TikTok",
                "Скрывать вкладки, которые TikTok добавит позже, пока ты их не разрешишь.",
                Settings.FEED_NAVIGATION_BLOCK_NEW_TABS
        ));

        addPreference(group(context, "Нижняя панель"));
        addPreference(new TogglePreference(
                context,
                "Фильтровать нижние вкладки",
                "Какие нижние вкладки TikTok оставить.",
                Settings.BOTTOM_NAVIGATION
        ));
        addPreference(new TabSelectionPreference(
                context,
                Settings.BOTTOM_NAVIGATION_TABS,
                true
        ));
        addPreference(new TogglePreference(
                context,
                "Блокировать новые нижние вкладки",
                "Скрывать нижние вкладки, которые TikTok добавит позже, пока ты их не разрешишь.",
                Settings.BOTTOM_NAVIGATION_BLOCK_NEW_TABS
        ));

        addPreference(group(context, "Прочая навигация"));
        addPreference(new TogglePreference(
                context,
                "Скрыть Tako AI",
                "Убрать пузырь Tako AI над кнопкой профиля.",
                Settings.HIDE_TAKO_AI
        ));
    }
}
