/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.provision.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import love.miao.yun.R;
import love.miao.yun.ai.AiManager;

import fan.appcompat.app.AlertDialog;
import fan.preference.PreferenceFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The guide's "this app's own settings" step.
 *
 * The page furniture is upstream's: a miuix {@link PreferenceFragment} inside its
 * `provision_detail_layout` shell (title, preview icon, bottom button group), preference
 * categories, and miuix preference rows. What the rows *are* changed — upstream uses this slot for
 * HyperCeiler's own settings (language, launcher icon, scope), this app uses it for the one thing a
 * first run has to configure: the AI endpoint. Three rows: 接口地址 → API Key → 模型.
 *
 * Every row reads and writes through {@link AiManager}, the same store the app's AI settings page
 * uses (`ui/miuix/ai/AiConfigScreen.kt`), so a value set here is the value the app uses. The rows are
 * non-persistent on purpose, which is also what upstream does for its own app settings
 * (`mLanguagePreference.setPersistent(false)` + `AppSettingsStore`): the write goes through the app's
 * own API rather than through a preference file belonging to this fragment.
 *
 * The model row is a plain {@link Preference} — click opens a dialog with a "获取模型" button and a
 * model list. The user manually triggers the fetch, avoiding the auto-fetch icon duplication issue
 * with the old DropDownPreference.
 */
public class BasicSettingsFragment extends PreferenceFragment {

    private static final String PREF_BASE_URL = "base_url";
    private static final String PREF_API_KEY = "api_key";
    private static final String PREF_MODEL = "model";

    private EditTextPreference mBaseUrlPreference;
    private EditTextPreference mApiKeyPreference;
    private Preference mModelPreference;

    /** Cached model list from the last successful fetch. */
    private final List<String> mCachedModels = new ArrayList<>();
    private boolean mFetching;

    /**
     * Back-press callback that intercepts the gesture while the model dialog is showing,
     * so the dialog dismisses with the system's predictive-back animation instead of the
     * activity's page-level animation swallowing the event.
     */
    private AlertDialog mModelDialog;
    private final OnBackPressedCallback mBackCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (mModelDialog != null && mModelDialog.isShowing()) {
                mModelDialog.dismiss();
            }
        }
    };

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.provision_basic_settings, rootKey);

        mBaseUrlPreference = findPreference(PREF_BASE_URL);
        mApiKeyPreference = findPreference(PREF_API_KEY);
        mModelPreference = findPreference(PREF_MODEL);

        for (Preference preference : new Preference[]{
            mBaseUrlPreference, mApiKeyPreference, mModelPreference
        }) {
            if (preference != null) {
                preference.setPersistent(false);
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);

        // Register the back callback so it is available before any dialog opens.
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), mBackCallback);

        AiManager.Config config = AiManager.INSTANCE.load(requireContext());

        if (mBaseUrlPreference != null) {
            mBaseUrlPreference.setText(config.getBaseUrl());
            mBaseUrlPreference.setSummary(config.getBaseUrl());
            mBaseUrlPreference.setOnBindEditTextListener(editText ->
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI));
            mBaseUrlPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                save(updated -> updated.setBaseUrl((String) newValue));
                preference.setSummary((CharSequence) newValue);
                // A different endpoint means the cached model list is stale.
                mCachedModels.clear();
                return true;
            });
        }

        if (mApiKeyPreference != null) {
            mApiKeyPreference.setText(config.getApiKey());
            showApiKeySummary(config.getApiKey());
            mApiKeyPreference.setOnBindEditTextListener(editText ->
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            mApiKeyPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                save(updated -> updated.setApiKey((String) newValue));
                showApiKeySummary((String) newValue);
                mCachedModels.clear();
                return true;
            });
        }

        if (mModelPreference != null) {
            showModelSummary(config.getModel());
            mModelPreference.setOnPreferenceClickListener(preference -> {
                showModelDialog();
                return true;
            });
        }
    }

    /**
     * One funnel for every edit: read the stored config, change the one field, write it back — so
     * the prompts (and anything else the app keeps in there) survive a visit to this page.
     */
    private void save(Consumer<AiManager.Config> block) {
        AiManager.Config config = AiManager.INSTANCE.load(requireContext());
        block.accept(config);
        AiManager.INSTANCE.save(requireContext(), config);
    }

    private void showApiKeySummary(String apiKey) {
        if (mApiKeyPreference == null) return;
        if (TextUtils.isEmpty(apiKey)) {
            mApiKeyPreference.setSummary(R.string.provision_ai_api_key_empty);
            return;
        }
        String key = apiKey.trim();
        // The row is a one-liner and the key is a secret, so only its tail is shown.
        String tail = key.length() <= 4 ? key : key.substring(key.length() - 4);
        mApiKeyPreference.setSummary(getString(R.string.provision_ai_api_key_set, tail));
    }

    private void showModelSummary(String model) {
        if (mModelPreference == null) return;
        mModelPreference.setSummary(TextUtils.isEmpty(model)
            ? getString(R.string.provision_ai_model_empty)
            : model);
    }

    /**
     * Show a dialog with a "获取模型" button and a model list. If models have been fetched before,
     * the list is shown directly; otherwise the user taps the button to fetch.
     *
     * The dialog is tracked in [mModelDialog] so the back callback can intercept the predictive-back
     * gesture and dismiss it with the system animation instead of letting the activity's page-level
     * animation swallow the event.
     */
    private void showModelDialog() {
        if (!isAdded()) return;

        AiManager.Config config = AiManager.INSTANCE.load(requireContext());
        String currentModel = config.getModel();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
            .setTitle(R.string.provision_ai_select_model);

        // If we have models, let the user pick one.
        if (!mCachedModels.isEmpty()) {
            int checkedIndex = -1;
            if (!TextUtils.isEmpty(currentModel)) {
                checkedIndex = mCachedModels.indexOf(currentModel);
            }
            builder.setSingleChoiceItems(
                mCachedModels.toArray(new CharSequence[0]),
                checkedIndex,
                (dialog, which) -> {
                    String selected = mCachedModels.get(which);
                    save(updated -> updated.setModel(selected));
                    showModelSummary(selected);
                    dialog.dismiss();
                }
            );
        }

        // "获取模型" button — always present so the user can refresh.
        builder.setNeutralButton(R.string.provision_ai_fetch_models, (dialog, which) -> {
            fetchModelsAndShowDialog();
        });

        // If no cached models, just show the empty state with the fetch button.
        if (mCachedModels.isEmpty()) {
            builder.setMessage(R.string.provision_ai_no_models);
        }

        builder.setNegativeButton(android.R.string.cancel, null);

        // Track the dialog for back-press interception.
        mModelDialog = builder.create();
        mModelDialog.setOnShowListener(dialog -> mBackCallback.setEnabled(true));
        mModelDialog.setOnDismissListener(dialog -> {
            mBackCallback.setEnabled(false);
            mModelDialog = null;
        });
        mModelDialog.show();
    }

    /**
     * Fetch models from the endpoint, then re-open the dialog with the results.
     */
    private void fetchModelsAndShowDialog() {
        if (mFetching || !isAdded()) return;
        mFetching = true;

        // Show a loading toast.
        Toast.makeText(requireContext(), R.string.provision_ai_models_loading, Toast.LENGTH_SHORT).show();

        AiManager.INSTANCE.listModels(AiManager.INSTANCE.load(requireContext()), new AiManager.ListCallback() {
            @Override
            public void onSuccess(List<String> models) {
                if (!isAdded()) return;
                mFetching = false;
                mCachedModels.clear();
                mCachedModels.addAll(models);

                // Re-open the dialog with the fetched list.
                if (isAdded()) {
                    showModelDialog();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                mFetching = false;
                Toast.makeText(requireContext(),
                    getString(R.string.provision_ai_models_failed, message),
                    Toast.LENGTH_LONG).show();
            }
        });
    }
}
