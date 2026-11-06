package app.morphe.extension.tiktok.settings.preference.categories;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.prism.PrismDialogs;
import app.morphe.extension.tiktok.prism.PrismIcons;
import app.morphe.extension.tiktok.prism.PrismTheme;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsMenuPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
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
                "Новый профиль",
                "Экспериментально: новая раскладка профиля из свежего TikTok, даже если сервер её тебе ещё не включил. Пока с ней могут пропадать счётчики и «Изменить профиль». Нужен перезапуск.",
                Settings.NEW_PROFILE_LAYOUT
        ));
        addPreference(new NumberInputPreference(
                context,
                "Вариант нового профиля",
                "TikTok тестирует 4 варианта новой раскладки (1–4), например с аватаркой справа. Нужен перезапуск.",
                Settings.NEW_PROFILE_LAYOUT_VARIANT,
                1,
                4
        ));
        addPreference(new app.morphe.extension.tiktok.settings.preference.ProfileDebugPreference(context));
        addPreference(new TogglePreference(
                context,
                "Украшения темы",
                "Реалистичные украшения в двух углах, справа сверху и слева снизу: паутина с пауком на Хеллоуин, "
                        + "бант, неон, золото, сакура и другие. Слегка покачиваются, над полем ввода прячутся.",
                Settings.PRISM_DECOR
        ));

        addPreference(new TogglePreference(
                context,
                "Проверять обновления",
                "При запуске Prisma смотрит, вышла ли новая версия, и предлагает обновиться в одно касание.",
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
                "Бейджи из репозитория Prisma",
                "Подтягивать общий список бейджей, который ведёт админ мода.",
                Settings.PRISM_REMOTE_BADGES
        ));
    }
}
