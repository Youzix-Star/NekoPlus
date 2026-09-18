/*
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Temporary stand-in while the permission page is being rewritten for this app: HyperCeiler's page
 * asks for the QUERY_ALL_PACKAGES permission (it needs the installed-app list to hook them), which
 * means nothing here. The page itself is being replaced by our two switches — accessibility service
 * and overlay — at which point this class goes away. Until then it keeps the ported file compiling.
 */
package com.sevtinge.hyperceiler.common.utils;

import android.content.Context;

public final class PermissionUtils {

    public static final String PERMISSION_GET_INSTALLED_APPS = "android.permission.QUERY_ALL_PACKAGES";

    private PermissionUtils() {
    }

    public static boolean hasInstalledAppsPermission(Context context) {
        return false;
    }

    public static boolean isInstalledAppsPermissionGranted(String[] permissions, int[] grantResults) {
        return false;
    }
}
