package app.morphe.extension.tiktok.settings.preference.categories;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.PreferenceScreen;
import android.view.View;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.preference.ClearLogBufferPreference;
import app.morphe.extension.shared.settings.preference.ExportDiagnosticReportPreference;
import app.morphe.extension.shared.settings.preference.LogExportFilterPreference;
import app.morphe.extension.tiktok.Utils;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class DebugPreferenceCategory extends ConditionalPreferenceCategory {
    public DebugPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Диагностика");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.diagnosticsEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (app.morphe.extension.tiktok.diagnostics.FeedObservationProbe.installed) {
            addPreference(app.morphe.extension.tiktok.diagnostics.FeedObservationProbe.controls(context));
        }
        addPreference(group(context, "Логи и сбои"));
        addPreference(new TogglePreference(
                context,
                "Включить диагностические логи",
                "Включай только чтобы записать логи для багрепорта. Если держать долго, TikTok начнёт тормозить и может вылетать.",
                BaseSettings.DEBUG
        ));

        addPreference(new TogglePreference(
                context,
                "Сохранять отчёты о сбоях",
                "Сохранять последний отчёт о сбое TikTok для экспорта.",
                BaseSettings.CAPTURE_JAVA_CRASHES
        ));

        addPreference(group(context, "Отчёты и данные"));
        var logFilter = new TintedLogExportFilterPreference(context);
        logFilter.setTitle("Что включать в отчёт");
        addPreference(logFilter);

        var exportLogs = new TintedExportDiagnosticReportPreference(context);
        exportLogs.setTitle("Экспорт отчёта");
        exportLogs.setSummary("Скопировать краткий отчёт или сохранить полный в файл.");
        addPreference(exportLogs);

        var clearLogs = new TintedClearLogBufferPreference(context);
        clearLogs.setTitle("Очистить диагностику");
        clearLogs.setSummary("Удалить накопленные события и отчёты о сбоях.");
        addPreference(clearLogs);
    }

    private static class TintedExportDiagnosticReportPreference extends ExportDiagnosticReportPreference {
        TintedExportDiagnosticReportPreference(Context context) {
            super(context);
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            Utils.setTitleAndSummaryColor(view);
        }

        @Override
        protected void onDialogShown(AlertDialog dialog) {
            SettingsUi.styleStandardAlertDialog(dialog);
        }
    }

    private static class TintedLogExportFilterPreference extends LogExportFilterPreference {
        TintedLogExportFilterPreference(Context context) {
            super(context);
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            Utils.setTitleAndSummaryColor(view);
        }

        @Override
        protected void onDialogShown(AlertDialog dialog) {
            SettingsUi.styleStandardAlertDialog(dialog);
        }
    }

    private static class TintedClearLogBufferPreference extends ClearLogBufferPreference {
        TintedClearLogBufferPreference(Context context) {
            super(context);
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            Utils.setTitleAndSummaryColor(view);
        }
    }
}
