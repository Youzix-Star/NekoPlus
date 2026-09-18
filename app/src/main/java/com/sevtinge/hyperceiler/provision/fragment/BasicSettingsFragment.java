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

import fan.preference.PreferenceFragment;

import java.util.List;
import java.util.function.Consumer;

/**
 * The guide's "this app's own settings" step.
 *
 * The page furniture is upstream's: a miuix {@link PreferenceFragment} inside its
 * `provision_detail_layout` shell (title, preview icon, bottom button group), preference
 * categories, and preference rows with their icons. What the rows *are* changed — upstream uses
 * this slot for HyperCeiler's own settings (language, launcher icon, scope), this app uses it for
 * the one thing a first run has to configure: the AI endpoint.
 *
 * Every row reads and writes through {@link AiManager}, the same store the app's AI settings page
 * uses (`ui/miuix/ai/AiConfigScreen.kt`), so a value typed here is the value the app uses. The rows
 * are non-persistent on purpose, which is also what upstream does for its own app settings
 * (`mLanguagePreference.setPersistent(false)` + `AppSettingsStore`): the write goes through the
 * app's own API rather than through a preference file belonging to this fragment.
 */
public class BasicSettingsFragment extends PreferenceFragment {

    private static final String PREF_BASE_URL = "base_url";
    private static final String PREF_API_KEY = "api_key";
    private static final String PREF_MODEL = "model";
    private static final String PREF_CONNECTION_TEST = "connection_test";

    private EditTextPreference mBaseUrlPreference;
    private EditTextPreference mApiKeyPreference;
    private EditTextPreference mModelPreference;
    private Preference mConnectionTestPreference;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.provision_basic_settings, rootKey);

        mBaseUrlPreference = findPreference(PREF_BASE_URL);
        mApiKeyPreference = findPreference(PREF_API_KEY);
        mModelPreference = findPreference(PREF_MODEL);
        mConnectionTestPreference = findPreference(PREF_CONNECTION_TEST);

        for (Preference preference : new Preference[]{
            mBaseUrlPreference, mApiKeyPreference, mModelPreference, mConnectionTestPreference
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
                return true;
            });
        }

        if (mModelPreference != null) {
            mModelPreference.setText(config.getModel());
            mModelPreference.setSummary(config.getModel());
            mModelPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                save(updated -> updated.setModel((String) newValue));
                preference.setSummary((CharSequence) newValue);
                return true;
            });
        }

        if (mConnectionTestPreference != null) {
            mConnectionTestPreference.setOnPreferenceClickListener(preference -> {
                runConnectionTest();
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

    /**
     * The connection test is the app's own model-list request (`GET {base}/models`): the one call
     * that proves the address, the key and the model name all work. It answers on the main thread.
     */
    private void runConnectionTest() {
        if (mConnectionTestPreference == null) return;
        mConnectionTestPreference.setEnabled(false);
        mConnectionTestPreference.setSummary(R.string.provision_ai_test_running);

        AiManager.INSTANCE.listModels(AiManager.INSTANCE.load(requireContext()), new AiManager.ListCallback() {
            @Override
            public void onSuccess(List<String> models) {
                if (!isAdded() || mConnectionTestPreference == null) return;
                mConnectionTestPreference.setEnabled(true);
                mConnectionTestPreference.setSummary(getString(R.string.provision_ai_test_ok, models.size()));
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || mConnectionTestPreference == null) return;
                mConnectionTestPreference.setEnabled(true);
                mConnectionTestPreference.setSummary(getString(R.string.provision_ai_test_failed, message));
            }
        });
    }
}
