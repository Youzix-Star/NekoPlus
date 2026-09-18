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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import love.miao.yun.R;
import love.miao.yun.service.MiaoAccessibilityService;
import com.sevtinge.hyperceiler.provision.widget.PermissionItemView;

/**
 * The permission step of the guide.
 *
 * HyperCeiler's version of this page checks four things it needs (network, root, installed apps,
 * LSPosed). This port keeps the page itself — its layout, its rows and how a row reports state —
 * but the rows are the two permissions this app actually asks for: the accessibility service and
 * the overlay permission. Both are granted in system settings, so the state can only change while
 * the guide is in the background; {@link #onResume()} is where it is re-read.
 */
public class PermissionSettingsFragment extends BaseFragment {

    PermissionItemView mAccessibilityPermissionItem;
    PermissionItemView mOverlayPermissionItem;

    @Override
    protected int getLayoutId() {
        return R.layout.provision_permission_layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAccessibilityPermissionItem = view.findViewById(R.id.accessibility);
        mOverlayPermissionItem = view.findViewById(R.id.overlay);

        mAccessibilityPermissionItem.setItemTitle(R.string.provision_permission_accessibility);
        mOverlayPermissionItem.setItemTitle(R.string.provision_permission_overlay);

        mAccessibilityPermissionItem.setOnClickListener(v -> openAccessibilitySettings());
        mOverlayPermissionItem.setOnClickListener(v -> requestOverlayPermission());

        checkAccessibility();
        checkOverlay();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAccessibility();
        checkOverlay();
    }

    private void checkAccessibility() {
        if (mAccessibilityPermissionItem == null) return;
        mAccessibilityPermissionItem.setChecked(MiaoAccessibilityService.Companion.isEnabled(requireContext()));
    }

    private void checkOverlay() {
        if (mOverlayPermissionItem == null) return;
        mOverlayPermissionItem.setChecked(Settings.canDrawOverlays(requireContext()));
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void requestOverlayPermission() {
        try {
            startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + requireContext().getPackageName())
            ));
        } catch (Exception ignored) {
        }
    }
}
