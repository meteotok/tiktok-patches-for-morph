/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/ExtensionPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class ExtensionPreferenceCategory extends ConditionalPreferenceCategory {
    public ExtensionPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Поведение");
    }

    @Override
    public boolean getSettingsStatus() {
        return true;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(group(context, "Ссылки и отправка"));
        addPreference(new TogglePreference(
                context,
                "Чистые ссылки",
                "Убирать трекинг-параметры из ссылок.",
                BaseSettings.SANITIZE_SHARING_LINKS
        ));
        if (SettingsStatus.externalBrowserEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Открывать ссылки в браузере",
                    "Ссылки из профилей и историй открываются в системном браузере, а не во встроенном.",
                    Settings.OPEN_EXTERNAL_LINKS
            ));
        }
        if (SettingsStatus.autoScrollEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Всегда показывать автопрокрутку",
                    "Показывать родную кнопку автопрокрутки на видео в «Рекомендациях», даже если "
                            + "аккаунт её пока не получил. Поддерживает ли видео и экран — всё равно решает TikTok.",
                    Settings.FORCE_SHOW_AUTO_SCROLL
            ));
        }

        addPreference(group(context, "Воспроизведение"));
        addPreference(new TogglePreference(
                context,
                "Показывать полосу перемотки",
                "Показывать полосу перемотки там, где TikTok её прячет.",
                Settings.SHOW_SEEKBAR
        ));
        if (SettingsStatus.seekbarThumbnailEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Превью при перемотке",
                    "Показывать превью кадра при перемотке.",
                    Settings.SHOW_SEEKBAR_THUMBNAIL
            ));
        }
        if (SettingsStatus.stopVideoLoopingEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Без зацикливания видео",
                    "Видео останавливается в конце, а не крутится заново.",
                    Settings.STOP_VIDEO_LOOPING
            ));
        }
        if (SettingsStatus.resumeVideoAfterScrollEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Продолжать видео после прокрутки",
                    "Продолжать видео с того места, где остановился, когда возвращаешься к нему.",
                    Settings.RESUME_VIDEO_AFTER_SCROLL
            ));
        }

        if (SettingsStatus.longPressSpeedLockEnabled
                || SettingsStatus.disableLongPressQuickShareEnabled
                || SettingsStatus.disableLongPressRepostEnabled) {
            addPreference(group(context, "Жесты"));
        }
        if (SettingsStatus.longPressSpeedLockEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "2x удержанием в любом месте",
                    "Зажми видео где угодно — ускорится до 2x, как в обычном TikTok. "
                            + "Меню долгого нажатия больше не вылезает. Нужен перезапуск.",
                    Settings.HOLD_ANYWHERE_2X
            ));
            addPreference(new TogglePreference(
                    context,
                    "Фиксация 2x удержанием и свайпом",
                    "Зажми, сдвинь вниз и отпусти — скорость 2x зафиксируется.",
                    Settings.ENABLE_LONG_PRESS_SPEED_LOCK
            ));
        }
        if (SettingsStatus.disableLongPressQuickShareEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Отключить быстрый репост долгим нажатием",
                    "Долгое нажатие на «Поделиться» больше не открывает быструю отправку.",
                    Settings.DISABLE_LONG_PRESS_QUICK_SHARE
            ));
        }
        if (SettingsStatus.disableLongPressRepostEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Отключить репост долгим нажатием на лайк",
                    "Долгое нажатие на лайк больше не открывает репост.",
                    Settings.DISABLE_LONG_PRESS_REPOST
            ));
        }

        if (SettingsStatus.hideSuggestedAccountsEnabled
                || SettingsStatus.nonPersonalizedSearchEnabled
                || SettingsStatus.liveSearchEnabled) {
            addPreference(group(context, "Поиск и рекомендации"));
        }
        if (SettingsStatus.hideSuggestedAccountsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть рекомендуемые аккаунты",
                    "Убрать рекомендуемые аккаунты из профиля и входящих.",
                    Settings.HIDE_SUGGESTED_ACCOUNTS
            ));
        }
        if (SettingsStatus.nonPersonalizedSearchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Поиск без персонализации",
                    "Поиск без персонализации, независимо от настроек аккаунта.",
                    Settings.ENABLE_NON_PERSONALIZED_SEARCH
            ));
        }
        if (SettingsStatus.liveSearchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Поиск в LIVE",
                    "Показывать поиск в панели LIVE, где это поддерживается.",
                    Settings.ENABLE_LIVE_SEARCH
            ));
        }
    }
}
