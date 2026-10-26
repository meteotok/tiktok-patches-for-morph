package app.morphe.extension.tiktok.settings.preference.categories;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.prism.PrismDialogs;
import app.morphe.extension.tiktok.prism.PrismIcons;
import app.morphe.extension.tiktok.prism.PrismTheme;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsMenuPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

/** Prism look: theme, launcher icon, liquid glass and badges. */
@SuppressWarnings("deprecation")
public final class AppearancePreferenceCategory extends ConditionalPreferenceCategory {
    public AppearancePreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Внешний вид");
    }

    @Override
    public boolean getSettingsStatus() {
        return true;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(group(context, "Стиль"));
        addPreference(new SettingsMenuPreference(
                context,
                "Тема",
                PrismTheme.current().title,
                SettingsMenuPreference.Icon.PALETTE,
                0,
                preference -> {
                    Activity activity = PrismDialogs.activityOf(preference.getContext());
                    if (activity != null) {
                        PrismDialogs.showThemePicker(activity);
                    }
                    return true;
                }
        ));
        addPreference(new SettingsMenuPreference(
                context,
                "Иконка приложения",
                PrismIcons.current().title,
                SettingsMenuPreference.Icon.APPS,
                0,
                preference -> {
                    Activity activity = PrismDialogs.activityOf(preference.getContext());
                    if (activity != null) {
                        PrismDialogs.showIconPicker(activity);
                    }
                    return true;
                }
        ));

        addPreference(new TogglePreference(
                context,
                "Украшения темы",
                "Рисунки темы в строке состояния и по краям экрана: паутина, паучок на нитке и тыквы на Хеллоуин, "
                        + "бантики, лепестки, пузыри и блёстки в других темах. Встают только на пустые места.",
                Settings.PRISM_DECOR
        ));

        addPreference(new TogglePreference(
                context,
                "Проверять обновления",
                "При запуске Prism смотрит, вышла ли новая версия, и предлагает обновиться в одно касание.",
                Settings.PRISM_UPDATE_CHECK
        ));

        addPreference(group(context, "Liquid glass"));
        addPreference(new TogglePreference(
                context,
                "Стеклянная нижняя панель",
                "Вкладки на парящей стеклянной пилюле, открытая вкладка подсвечена цветом темы.",
                Settings.PRISM_GLASS_NAV
        ));


        addPreference(group(context, "Бейджи"));
        addPreference(new TogglePreference(
                context,
                "Бейджи у ников",
                "Значок рядом с никами, которым его выдали в Admin Panel.",
                Settings.PRISM_BADGES
        ));
        addPreference(new TogglePreference(
                context,
                "Бейджи из репозитория Prism",
                "Подтягивать общий список бейджей, который ведёт админ мода.",
                Settings.PRISM_REMOTE_BADGES
        ));
    }
}
