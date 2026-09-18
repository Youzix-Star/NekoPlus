/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Taken from the original NekoNeko / MiaoAssistant app, which shipped exactly this trick.
 */

package com.google.android.accessibility.selecttospeak

import love.miao.yun.service.MiaoAccessibilityService

/**
 * The service the system actually binds — under a name that is not ours.
 *
 * WeChat 8.0.52 and later hand an accessibility service an **empty node tree** when it does not
 * recognise it: the window is still reported, its root comes back as a single node with no class,
 * no bounds and no children. That is what "the input box cannot be found in WeChat" looks like
 * from the inside, and no amount of tree-walking fixes it — there is no tree to walk.
 *
 * Registering the service as `com.google.android.accessibility.selecttospeak.SelectToSpeakService`,
 * a component name the system itself uses for its own select-to-speak service, gets past that
 * check. Every behaviour lives in [MiaoAccessibilityService]; this class exists only to carry the
 * name, and the manifest declares this one, never the real name — declaring both would put two
 * entries in system settings and hand WeChat the one it is looking for.
 *
 * The cost is honest and worth stating: the component name in the system's accessibility settings
 * is no longer ours, and anyone who had the previous name enabled has to switch the service on
 * again, because the platform keeps the old component name in its list and it no longer resolves.
 */
class SelectToSpeakService : MiaoAccessibilityService()
