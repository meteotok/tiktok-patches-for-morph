package app.morphe.extension.tiktok.settings.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.tiktok.navigation.BottomNavigationTabOptions;
import app.morphe.extension.tiktok.navigation.NavigationTabOptions;
import app.morphe.extension.tiktok.settings.Settings;

@SuppressWarnings("deprecation")
public class TabSelectionPreference extends Preference {
    private final StringSetting setting;
    private final boolean bottomTabs;
    private String value;
    private boolean valueSet;

    public TabSelectionPreference(Context context, StringSetting setting) {
        this(context, setting, false);
    }

    public TabSelectionPreference(Context context, StringSetting setting, boolean bottomTabs) {
        super(context);
        this.setting = setting;
        this.bottomTabs = bottomTabs;
        setTitle(bottomTabs ? "Разрешённые нижние вкладки" : "Разрешённые вкладки");
        setKey(setting.key);
        setValue(setting.get());
    }

    public String getValue() {
        return value;
    }

    public boolean setValue(String value) {
        String sanitizedValue = serializeEnabledKeys(parseEnabledKeys(value));
        boolean changed = !TextUtils.equals(this.value, sanitizedValue);
        if (changed || !valueSet) {
            this.value = sanitizedValue;
            valueSet = true;
            setting.save(sanitizedValue);
            refreshSummary();
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
        return changed;
    }

    @Override
    protected void onClick() {
        showSelectionDialog();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }

    private void refreshSummary() {
        Set<String> selected = parseEnabledKeys(value);
        List<OptionRow> observedOptions = getObservedOptions();
        if (observedOptions.size() <= 1) {
            setSummary(bottomTabs ? "Открой главную TikTok, чтобы найти нижние вкладки." : "Открой главную ленту TikTok, чтобы найти вкладки.");
            return;
        }

        int selectedObserved = 0;
        StringBuilder builder = new StringBuilder();
        for (OptionRow option : observedOptions) {
            if (!selected.contains(option.key)) {
                continue;
            }

            selectedObserved++;
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(option.label);
        }

        if (selectedObserved == observedOptions.size()) {
            setSummary("Все загруженные вкладки");
            return;
        }

        if (builder.length() == 0) {
            setSummary(bottomTabs ? "Главная" : "Рекомендации");
            return;
        }
        setSummary(builder.toString());
    }

    private void showSelectionDialog() {
        Context context = getContext();
        Set<String> selected = new LinkedHashSet<>(parseEnabledKeys(value));
        List<OptionRow> observedOptions = getObservedOptions();

        LinearLayout dialogView = new LinearLayout(context);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setBackground(createDialogBackground());
        int padding = dpToPx(22);
        dialogView.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(context);
        title.setText(bottomTabs ? "Разрешённые нижние вкладки" : "Разрешённые вкладки");
        title.setTextColor(getTitleTextColor());
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        dialogView.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView helper = new TextView(context);
        helper.setText(bottomTabs
                ? "Тут только нижние вкладки, которые TikTok загрузил на этом устройстве. Недоступные вкладки это не включит."
                : "Тут только вкладки, которые TikTok загрузил на этом устройстве. Недоступные вкладки это не включит.");
        helper.setTextColor(getSummaryTextColor());
        LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        helperParams.setMargins(0, dpToPx(16), 0, dpToPx(12));
        dialogView.addView(helper, helperParams);

        LinearLayout optionsContainer = new LinearLayout(context);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        optionsContainer.setBackground(createListBackground());
        int optionInset = Math.max(1, dpToPx(1));
        optionsContainer.setPadding(optionInset, optionInset, optionInset, optionInset);

        for (OptionRow option : observedOptions) {
            optionsContainer.addView(createOptionRow(context, selected, option));
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.addView(optionsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                SettingsUi.dialogListHeight(context, 380)
        );
        scrollParams.setMargins(0, 0, 0, dpToPx(16));
        dialogView.addView(scrollView, scrollParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView showAllButton = createActionButton(context, "Сбросить", false);
        TextView cancelButton = createActionButton(context, "Отмена", false);
        TextView saveButton = createActionButton(context, "Сохранить", true);

        actions.addView(showAllButton, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        actions.addView(cancelButton);
        actions.addView(saveButton);
        dialogView.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        showAllButton.setOnClickListener(view -> {
            selected.clear();
            for (OptionRow option : observedOptions) {
                selected.add(option.key);
            }
            boolean changed = setValue(serializeEnabledKeys(selected));
            dialog.dismiss();
            if (changed && setting.rebootApp) {
                AbstractPreferenceFragment.showRestartDialog(context);
            }
        });
        cancelButton.setOnClickListener(view -> dialog.dismiss());
        saveButton.setOnClickListener(view -> {
            boolean changed = setValue(serializeEnabledKeys(selected));
            dialog.dismiss();
            if (changed && setting.rebootApp) {
                AbstractPreferenceFragment.showRestartDialog(context);
            }
        });

        dialog.show();
        SettingsUi.styleDialog(dialog);
    }

    private List<OptionRow> getObservedOptions() {
        LinkedHashSet<OptionRow> rows = new LinkedHashSet<>();
        if (bottomTabs) {
            Set<String> observed = BottomNavigationTabOptions.parseObservedKeys(Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.get());
            observed.add(BottomNavigationTabOptions.HOME);
            observed.add(BottomNavigationTabOptions.PROFILE);
            for (BottomNavigationTabOptions.Option option : BottomNavigationTabOptions.optionsForKeys(observed)) {
                rows.add(new OptionRow(option.key, option.label));
            }
        } else {
            Set<String> observed = NavigationTabOptions.parseObservedKeys(Settings.FEED_NAVIGATION_OBSERVED_TABS.get());
            observed.add(NavigationTabOptions.HOT);
            for (NavigationTabOptions.Option option : NavigationTabOptions.optionsForKeys(observed)) {
                rows.add(new OptionRow(option.key, option.label));
            }
        }
        return new java.util.ArrayList<>(rows);
    }

    private View createOptionRow(Context context, Set<String> selected, OptionRow option) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getDialogBackgroundColor());
        row.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        CheckBox checkBox = new CheckBox(context);
        checkBox.setChecked(selected.contains(option.key));
        checkBox.setEnabled(!isRequiredOption(option.key));
        checkBox.setClickable(false);
        SettingsUi.styleCheckBox(checkBox);
        row.addView(checkBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(context);
        label.setText(option.label);
        label.setTextColor(getTitleTextColor());
        label.setTextSize(16);
        textContainer.addView(label);

        if (isRequiredOption(option.key)) {
            TextView summary = new TextView(context);
            summary.setText("Обязательно");
            summary.setTextColor(getSummaryTextColor());
            summary.setTextSize(13);
            textContainer.addView(summary);
        }

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        row.addView(textContainer, textParams);

        row.setOnClickListener(view -> {
            if (isRequiredOption(option.key)) {
                return;
            }

            if (selected.contains(option.key)) {
                selected.remove(option.key);
                checkBox.setChecked(false);
            } else {
                selected.add(option.key);
                checkBox.setChecked(true);
            }
        });

        View divider = new View(context);
        divider.setBackgroundColor(getDialogDividerColor());

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dpToPx(1))
        ));
        return wrapper;
    }

    private TextView createActionButton(Context context, String text, boolean primary) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(6));
        SettingsUi.styleTextAction(button, primary);
        return button;
    }

    private Set<String> parseEnabledKeys(String value) {
        return bottomTabs
                ? BottomNavigationTabOptions.parseEnabledKeys(value)
                : NavigationTabOptions.parseEnabledKeys(value);
    }

    private String serializeEnabledKeys(Set<String> selected) {
        return bottomTabs
                ? BottomNavigationTabOptions.serializeEnabledKeys(selected)
                : NavigationTabOptions.serializeEnabledKeys(selected);
    }

    private boolean isRequiredOption(String key) {
        return bottomTabs
                ? BottomNavigationTabOptions.isRequiredKey(key)
                : NavigationTabOptions.HOT.equals(key);
    }

    private static final class OptionRow {
        final String key;
        final String label;

        OptionRow(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }

    private GradientDrawable createDialogBackground() {
        return SettingsUi.borderedSurface(getContext(), 6, true);
    }

    private GradientDrawable createListBackground() {
        return SettingsUi.borderedSurface(getContext(), 4, false);
    }

    private static int getDialogBackgroundColor() {
        return SettingsUi.surface();
    }

    private static int getDialogDividerColor() {
        return SettingsUi.divider();
    }

    private static int getTitleTextColor() {
        return SettingsUi.textPrimary();
    }

    private static int getSummaryTextColor() {
        return SettingsUi.textSecondary();
    }
}
