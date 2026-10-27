/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/DownloadsPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.DownloadPathPreference;
import app.morphe.extension.tiktok.settings.preference.DownloadQualityPreference;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.download.DownloadDestination;
import app.morphe.extension.tiktok.offline.CustomOfflineVideosLimitPatch;

@SuppressWarnings("deprecation")
public class DownloadsPreferenceCategory extends ConditionalPreferenceCategory {
    public DownloadsPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Скачивание");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.downloadEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(group(context, "Скачивание видео"));
        addPreference(new DownloadQualityPreference(
                context,
                Settings.DOWNLOAD_VIDEO_QUALITY
        ));
        addPreference(new TogglePreference(
                context,
                "Без водяного знака",
                "Для скачивания видео и картинок.",
                Settings.DOWNLOAD_WATERMARK
        ));

        addPreference(group(context, "Куда сохранять"));
        addPreference(new DownloadPathPreference(
                context,
                "Папка для видео",
                Settings.DOWNLOAD_VIDEO_PATH,
                DownloadDestination.Kind.VIDEO
        ));
        addPreference(new DownloadPathPreference(
                context,
                "Папка для фото",
                Settings.DOWNLOAD_PHOTO_PATH,
                DownloadDestination.Kind.PHOTO
        ));
        addPreference(new DownloadPathPreference(
                context,
                "Папка для стикеров",
                Settings.DOWNLOAD_STICKER_PATH,
                DownloadDestination.Kind.STICKER
        ));

        addPreference(group(context, "Имена файлов"));
        addPreference(new InputTextPreference(
                context,
                "Имя файла видео",
                "Шаблоны: {creator}, {date}, {video_id}, {story_id}. {story_id} — то же, что ID видео. Расширение подставится само.",
                Settings.DOWNLOAD_VIDEO_FILENAME_TEMPLATE
        ));
        addPreference(new InputTextPreference(
                context,
                "Имя файла фото",
                "Шаблоны: {creator}, {date}, {video_id}, {story_id}, {index}. {story_id} — то же, что ID видео. Расширение подставится само.",
                Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE
        ));
        addPreference(new InputTextPreference(
                context,
                "Имя файла медиа из комментариев",
                "Шаблоны: {date}, {media_id}. Для картинок и видео-стикеров.",
                Settings.DOWNLOAD_COMMENT_MEDIA_FILENAME_TEMPLATE
        ));

        addPreference(group(context, "Офлайн-просмотр"));
        addPreference(new TogglePreference(
                context,
                "Своё число офлайн-видео",
                "Добавляет свой вариант в меню офлайн-видео TikTok (после перезапуска).",
                Settings.CUSTOM_OFFLINE_VIDEOS
        ));
        addPreference(new NumberInputPreference(
                context,
                "Лимит офлайн-видео",
                "От 1 до 1000 видео. Значения вне диапазона округляются до ближайшего. После сохранения перезапусти TikTok.",
                Settings.CUSTOM_OFFLINE_VIDEO_LIMIT,
                CustomOfflineVideosLimitPatch.MIN_LIMIT,
                CustomOfflineVideosLimitPatch.MAX_LIMIT
        ));

    }
}
