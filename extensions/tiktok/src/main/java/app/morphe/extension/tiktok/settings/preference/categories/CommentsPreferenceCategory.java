package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class CommentsPreferenceCategory extends ConditionalPreferenceCategory {
    public CommentsPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Комментарии");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.commentTranslationEnabled
                || SettingsStatus.hideCommentQuickReactionsEnabled
                || SettingsStatus.copyCommentsWithoutUsernameEnabled
                || SettingsStatus.commentSortControlsEnabled
                || SettingsStatus.foldableSplitViewEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.commentSortControlsEnabled) {
            addPreference(group(context, "Сортировка комментариев"));
            addPreference(new TogglePreference(
                    context,
                    "Всегда показывать сортировку комментариев",
                    "Показывать полное родное меню сортировки, не дожидаясь, пока TikTok его включит. "
                            + "Включая «Популярные» и «По времени». Сортировки «Медиа» и «Автор» "
                            + "появляются, только если такие комментарии есть.",
                    Settings.COMMENT_SORT_FORCE_SHOW
            ));
        }

        if (SettingsStatus.commentTranslationEnabled) {
            addPreference(group(context, "Перевод"));
            addPreference(new TogglePreference(
                    context,
                    "Автоперевод комментариев",
                    "Автоматически переводит загруженные комментарии встроенным переводчиком TikTok.",
                    Settings.COMMENT_BATCH_TRANSLATION
            ));
        }

        if (SettingsStatus.hideCommentQuickReactionsEnabled
                || SettingsStatus.copyCommentsWithoutUsernameEnabled) {
            addPreference(group(context, "Действия с комментариями"));
        }
        if (SettingsStatus.hideCommentQuickReactionsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Скрыть быстрые реакции",
                    "Убрать строку быстрых эмодзи над полем комментария.",
                    Settings.HIDE_COMMENT_QUICK_REACTIONS
            ));
        }
        if (SettingsStatus.copyCommentsWithoutUsernameEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Копировать комментарии без ника",
                    "При копировании комментария копируется только текст.",
                    Settings.COPY_COMMENTS_WITHOUT_USERNAME
            ));
        }

        if (SettingsStatus.foldableSplitViewEnabled) {
            addPreference(group(context, "Большие экраны"));
            addPreference(new TogglePreference(
                    context,
                    "Видео и комментарии рядом",
                    "Показывать комментарии рядом с видео, а не снизу, если ширина экрана не меньше "
                            + "указанного ниже порога. Для складных, которые TikTok не "
                            + "считает планшетами. Нужен перезапуск.",
                    Settings.FOLDABLE_SPLIT_VIEW
            ));
            addPreference(new NumberInputPreference(
                    context,
                    "Порог ширины для режима «рядом» (dp)",
                    "Минимальная ширина экрана в dp для режима «рядом». Экспортируй отчёт "
                            + "после открытия комментариев, чтобы узнать ширину экрана. "
                            + "Нужен перезапуск.",
                    Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP,
                    200,
                    1200
            ));
        }
    }
}
