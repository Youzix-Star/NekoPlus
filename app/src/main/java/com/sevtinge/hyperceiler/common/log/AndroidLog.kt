/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * HyperCeiler's own AndroidLog is a thin wrapper that also feeds its in-app log viewer, which pulls
 * in half of its `common` module. The ported provisioning code only ever calls d/i/w, so this keeps
 * the same package and the same signatures and forwards to the platform log.
 */
package com.sevtinge.hyperceiler.common.log

import android.util.Log

object AndroidLog {

    @JvmStatic
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}
