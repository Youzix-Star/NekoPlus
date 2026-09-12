package com.google.android.accessibility.selecttospeak

import love.miao.yun.service.MiaoAccessibilityService

/**
 * 伪装成系统内置无障碍服务，绕过微信 v8.0.52+ 的节点混淆。
 * 所有逻辑继承自 MiaoAccessibilityService。
 */
class SelectToSpeakService : MiaoAccessibilityService()
