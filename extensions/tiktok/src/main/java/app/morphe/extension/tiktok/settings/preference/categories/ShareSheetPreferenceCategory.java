package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ShareSheetItemSelectionPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.sharesheet.ShareChannelOptions;
import app.morphe.extension.tiktok.sharesheet.VideoActionOptions;

@SuppressWarnings("deprecation")
public class ShareSheetPreferenceCategory extends ConditionalPreferenceCategory {
    public ShareSheetPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Меню «Поделиться»");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.shareSheetEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(group(context, "Быстрая отправка"));
        addPreference(new TogglePreference(
                context,
                "Показывать «Отправить»",
                "Контакты для быстрой отправки сверху меню.",
                Settings.SHARE_SHEET_SEND_TO
        ));

        addPreference(group(context, "Приложения"));
        addPreference(new TogglePreference(
                context,
                "Показывать «Поделиться через»",
                "Строка приложений (Репост, Ссылка, Discord, WhatsApp…).",
                Settings.SHARE_SHEET_CHANNELS
        ));
        addPreference(new ShareSheetItemSelectionPreference(
                context,
                "Разрешённые приложения",
                "Разрешённые приложения",
                "Тут только приложения, которые TikTok уже показал на этом устройстве. Открой меню «Поделиться» "
                        + "один раз, чтобы найти приложения; новые сразу включены.",
                Settings.SHARE_SHEET_CHANNELS_ENABLED,
                ShareChannelOptions::parseEnabledKeys,
                ShareChannelOptions::serializeEnabledKeys,
                keys -> toRows(ShareChannelOptions.optionsForKeys(keys)),
                ShareSheetPreferenceCategory::observedChannelKeys
        ));

        addPreference(group(context, "Действия с видео"));
        addPreference(new TogglePreference(
                context,
                "Показывать «Действия с видео»",
                "Сетка действий (Пожаловаться, Скачать, Дуэт, Сшивка, Скорость…).",
                Settings.SHARE_SHEET_ACTIONS
        ));
        addPreference(new ShareSheetItemSelectionPreference(
                context,
                "Разрешённые действия",
                "Разрешённые действия",
                "Тут только действия, которые TikTok уже показал на этом устройстве. Открой меню «Поделиться» "
                        + "один раз, чтобы найти действия; новые сразу включены.",
                Settings.SHARE_SHEET_ACTIONS_ENABLED,
                VideoActionOptions::parseEnabledKeys,
                VideoActionOptions::serializeEnabledKeys,
                keys -> toRowsFromActions(VideoActionOptions.optionsForKeys(keys)),
                ShareSheetPreferenceCategory::observedActionKeys
        ));
    }

    private static Set<String> observedChannelKeys() {
        Set<String> keys = ShareChannelOptions.parseEnabledKeys(Settings.SHARE_SHEET_CHANNELS_ENABLED.get());
        keys.addAll(ShareChannelOptions.parseObservedKeys(Settings.SHARE_SHEET_CHANNELS_OBSERVED.get()));
        return keys;
    }

    private static Set<String> observedActionKeys() {
        Set<String> keys = VideoActionOptions.parseEnabledKeys(Settings.SHARE_SHEET_ACTIONS_ENABLED.get());
        keys.addAll(VideoActionOptions.parseObservedKeys(Settings.SHARE_SHEET_ACTIONS_OBSERVED.get()));
        return keys;
    }

    private static List<ShareSheetItemSelectionPreference.Row> toRows(List<ShareChannelOptions.Option> options) {
        List<ShareSheetItemSelectionPreference.Row> rows = new ArrayList<>();
        for (ShareChannelOptions.Option option : options) {
            rows.add(new ShareSheetItemSelectionPreference.Row(option.key, option.label));
        }
        return rows;
    }

    private static List<ShareSheetItemSelectionPreference.Row> toRowsFromActions(
            List<VideoActionOptions.Option> options
    ) {
        List<ShareSheetItemSelectionPreference.Row> rows = new ArrayList<>();
        for (VideoActionOptions.Option option : options) {
            rows.add(new ShareSheetItemSelectionPreference.Row(option.key, option.label));
        }
        return rows;
    }
}
