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

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import love.miao.yun.R;
import love.miao.yun.ai.AiManager;

import fan.preference.DropDownPreference;
import fan.preference.PreferenceFragment;

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
 * The model row is a {@link DropDownPreference} — the same class upstream's own language/icon rows
 * use — filled from the endpoint's model list. That list is the app's own `GET {base}/models`
 * request ({@link AiManager#listModels}), so *fetching it is the connection test*: there is no
 * separate test row, and a failure is reported where the user is looking, in the row's summary.
 */
public class BasicSettingsFragment extends PreferenceFragment {

    private static final String PREF_BASE_URL = "base_url";
    private static final String PREF_API_KEY = "api_key";
    private static final String PREF_MODEL = "model";

    private EditTextPreference mBaseUrlPreference;
    private EditTextPreference mApiKeyPreference;
    private DropDownPreference mModelPreference;

    /** The model list is in the dropdown; until it is, tapping the row retries the fetch. */
    private boolean mModelsLoaded;
    private boolean mFetching;

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

        AiManager.Config config = AiManager.INSTANCE.load(requireContext());

        if (mBaseUrlPreference != null) {
            mBaseUrlPreference.setText(config.getBaseUrl());
            mBaseUrlPreference.setSummary(config.getBaseUrl());
            mBaseUrlPreference.setOnBindEditTextListener(editText ->
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI));
            mBaseUrlPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                save(updated -> updated.setBaseUrl((String) newValue));
                preference.setSummary((CharSequence) newValue);
                // A different endpoint means a different model list.
                mModelsLoaded = false;
                fetchModels();
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
                mModelsLoaded = false;
                fetchModels();
                return true;
            });
        }

        if (mModelPreference != null) {
            showModelSummary(config.getModel());
            mModelPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                save(updated -> updated.setModel((String) newValue));
                showModelSummary((String) newValue);
                return true;
            });
            // The dropdown only has something to open once the list is in; before that (and after a
            // failed fetch) a tap retries instead of opening an empty menu.
            mModelPreference.setOnPreferenceClickListener(preference -> {
                if (mModelsLoaded) {
                    return false;
                }
                fetchModels();
                return true;
            });
            fetchModels();
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
     * Ask the endpoint which models it serves, through the app's own call, and put the answer in the
     * dropdown (names and values are the same string: those are the ids the API takes). The summary
     * carries the outcome either way, so "拉取失败" and "列表是空的" cannot be confused — the app's
     * call reports an empty list as an error of its own.
     */
    private void fetchModels() {
        if (mFetching || mModelPreference == null || !isAdded()) return;
        mFetching = true;
        mModelPreference.setSummary(R.string.provision_ai_models_loading);

        AiManager.INSTANCE.listModels(AiManager.INSTANCE.load(requireContext()), new AiManager.ListCallback() {
            @Override
            public void onSuccess(List<String> models) {
                if (!isAdded() || mModelPreference == null) return;
                mFetching = false;
                mModelsLoaded = true;

                CharSequence[] values = models.toArray(new CharSequence[0]);
                mModelPreference.setEntryValues(values);
                mModelPreference.setEntries(values);

                String saved = AiManager.INSTANCE.load(requireContext()).getModel();
                if (!TextUtils.isEmpty(saved) && mModelPreference.findIndexOfValue(saved) >= 0) {
                    mModelPreference.setValue(saved);
                    showModelSummary(saved);
                } else {
                    // Keep telling the truth about what is stored, and say why the dropdown does not
                    // show it: it is not one of the models this endpoint reports.
                    mModelPreference.setSummary(TextUtils.isEmpty(saved)
                        ? getString(R.string.provision_ai_model_empty)
                        : getString(R.string.provision_ai_model_not_in_list, saved));
                }
                // No notifyChanged() here on purpose: it is `protected` in their class, so a caller
                // outside `fan.preference` cannot use it. It is not needed either — their
                // setEntries() posts the rebind itself through mNotifyHandler (visible in the AAR's
                // method table: setEntries → mNotifyHandler), which is how upstream fills its own
                // language row from a Fragment.
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || mModelPreference == null) return;
                mFetching = false;
                mModelPreference.setSummary(getString(R.string.provision_ai_models_failed, message));
            }
        });
    }
}
