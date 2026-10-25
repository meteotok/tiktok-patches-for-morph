/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public final class InterfacePreferenceCategory extends ConditionalPreferenceCategory {
    public InterfacePreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Интерфейс");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.captchaPopupSuppressionEnabled
                || SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.hideFeedFollowButtonEnabled
                || SettingsStatus.hideFeedSaveButtonEnabled
                || SettingsStatus.hideFeedLiveButtonEnabled
                || SettingsStatus.hideFeedSearchButtonEnabled
                || SettingsStatus.alwaysShowPublishDateEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.hideFeedFollowButtonEnabled
                || SettingsStatus.hideFeedSaveButtonEnabled
                || SettingsStatus.hideFeedLiveButtonEnabled
                || SettingsStatus.hideFeedSearchButtonEnabled) {
            addPreference(group(context, "Лента"));
        }
        if (SettingsStatus.hideFeedFollowButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть кнопку подписки",
                    "Убрать «+» подписки под аватаркой автора. Нужен перезапуск.",
                    Settings.HIDE_FEED_FOLLOW_BUTTON
            ));
        }
        if (SettingsStatus.hideFeedSaveButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть кнопку «Избранное»",
                    "Убрать кнопку «Избранное» с панели справа. Нужен перезапуск.",
                    Settings.HIDE_FEED_SAVE_BUTTON
            ));
        }
        if (SettingsStatus.hideFeedLiveButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть кнопку LIVE",
                    "Убрать кнопку LIVE слева сверху. Нужен перезапуск.",
                    Settings.HIDE_FEED_LIVE_BUTTON
            ));
        }
        if (SettingsStatus.hideFeedSearchButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть кнопку поиска",
                    "Убрать кнопку поиска справа сверху. Нужен перезапуск.",
                    Settings.HIDE_FEED_SEARCH_BUTTON
            ));
        }

        if (SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.captchaPopupSuppressionEnabled) {
            addPreference(group(context, "Промо и окна"));
        }
        if (SettingsStatus.promotionalBannersEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть плавающее промо",
                    "Убрать плавающие промо-значки, монетки и баннеры с таймером на главной.",
                    Settings.HIDE_HOMEPAGE_COIN
            ));
        }
        if (SettingsStatus.captchaPopupSuppressionEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть капчи",
                    "Убрать капчи-пазлы в ленте и LIVE. Вход и проверка аккаунта работают как обычно.",
                    Settings.HIDE_CAPTCHA_POPUPS
            ));
        }

        if (SettingsStatus.alwaysShowPublishDateEnabled) {
            addPreference(group(context, "Информация о видео"));
            addPreference(new TogglePreference(
                    context,
                    "Всегда показывать дату публикации",
                    "Показывать дату публикации рядом с автором видео. Нужен перезапуск.",
                    Settings.ALWAYS_SHOW_PUBLISH_DATE
            ));
        }
    }
}
