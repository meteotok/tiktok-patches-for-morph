/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/SimSpoofPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.SimPresetPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class SimSpoofPreferenceCategory extends ConditionalPreferenceCategory {
    public SimSpoofPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Подмена региона");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.simSpoofEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(group(context, "Выбор региона"));
        addPreference(new TogglePreference(
                context,
                "Свой регион",
                "Использовать выбранный регион вместо региона устройства.",
                Settings.SIM_SPOOF
        ));
        InputTextPreference countryIsoPreference = new InputTextPreference(
                context,
                "Код страны (ISO)", "us, gb, jp, ...",
                Settings.SIM_SPOOF_ISO
        );
        InputTextPreference mccMncPreference = new InputTextPreference(
                context,
                "MCC/MNC оператора", "Например: 310260",
                Settings.SIMSPOOF_MCCMNC
        );
        InputTextPreference operatorNamePreference = new InputTextPreference(
                context,
                "Название оператора", "Например: T-Mobile",
                Settings.SIMSPOOF_OP_NAME
        );
        SimPresetPreference simPresetPreference = new SimPresetPreference(
                context,
                countryIsoPreference,
                mccMncPreference,
                operatorNamePreference
        );

        countryIsoPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            simPresetPreference.refreshSummary(
                    newValue.toString(),
                    mccMncPreference.getText(),
                    operatorNamePreference.getText()
            );
            return true;
        });
        mccMncPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            simPresetPreference.refreshSummary(
                    countryIsoPreference.getText(),
                    newValue.toString(),
                    operatorNamePreference.getText()
            );
            return true;
        });
        operatorNamePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            simPresetPreference.refreshSummary(
                    countryIsoPreference.getText(),
                    mccMncPreference.getText(),
                    newValue.toString()
            );
            return true;
        });

        addPreference(simPresetPreference);

        addPreference(group(context, "Оператор вручную"));
        addPreference(countryIsoPreference);
        addPreference(mccMncPreference);
        addPreference(operatorNamePreference);
    }
}
