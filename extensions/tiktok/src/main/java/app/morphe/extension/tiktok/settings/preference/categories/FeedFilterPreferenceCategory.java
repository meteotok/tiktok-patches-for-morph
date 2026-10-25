/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/FeedFilterPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.RangeValuePreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class FeedFilterPreferenceCategory extends ConditionalPreferenceCategory {
    public FeedFilterPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Лента");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.feedFilterEnabled || SettingsStatus.hideAiContentEnabled
                || SettingsStatus.hideFypSlopEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.feedFilterEnabled) {
            addPreference(group(context, "Типы контента"));
            addPreference(new TogglePreference(
                    context,
                    "Убрать рекламу", "Убрать рекламу из ленты.",
                    Settings.REMOVE_ADS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Скрыть TikTok Shop", "Убрать товары TikTok Shop из ленты.",
                    Settings.HIDE_SHOP
            ));
            addPreference(new TogglePreference(
                    context,
                    "Скрыть трансляции", "Убрать LIVE-трансляции из ленты.",
                    Settings.HIDE_LIVE
            ));
            addPreference(new TogglePreference(
                    context,
                    "Скрыть истории", "Убрать истории из ленты.",
                    Settings.HIDE_STORY
            ));
            addPreference(new TogglePreference(
                    context,
                    "Скрыть фото-посты", "Убрать фото-посты из ленты.",
                    Settings.HIDE_IMAGE
            ));
        }
        if (SettingsStatus.hideAiContentEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть ИИ-контент",
                    "Скрывать посты, помеченные как созданные или изменённые ИИ. Непомеченный ИИ-контент всё равно может попадаться.",
                    Settings.HIDE_AI_CONTENT
            ));
        }
        if (SettingsStatus.hideFypSlopEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть неперсонализированный мусор",
                    "Скрывает пачки неперсонализированных мусорных постов в «Рекомендациях».",
                    Settings.HIDE_ALTERNATE_FOR_YOU_BATCHES
            ));
        }

        if (SettingsStatus.feedFilterEnabled) {
            addPreference(group(context, "Фильтр по популярности"));
            addPreference(new RangeValuePreference(
                    context,
                    "Просмотры: мин/макс", "Показывать видео только с таким числом просмотров.",
                    Settings.MIN_MAX_VIEWS
            ));
            addPreference(new RangeValuePreference(
                    context,
                    "Лайки: мин/макс", "Показывать видео только с таким числом лайков.",
                    Settings.MIN_MAX_LIKES
            ));

            addPreference(group(context, "Запасные офлайн-видео"));
            addPreference(new TogglePreference(
                    context,
                    "Фильтровать запасные офлайн-видео",
                    "Применять фильтры контента и популярности к скачанным запасным видео. Фильтры ИИ и мусора в «Рекомендациях» работают отдельно.",
                    Settings.FILTER_OFFLINE_FALLBACK_VIDEOS
            ));
        }
    }
}
