package com.focus.moment.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focus.moment.MainActivity

/**
 * 锁机服务（严格模式）：自律计时期间，一旦检测到其他应用窗口出现在前台，
 * 立即将本 App 拉回，实现"不能切屏、不能退出"的学霸模式效果。
 */
class FocusAccessibilityService : AccessibilityService() {

    private var lastPull = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!FocusLockController.lockActive) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == "com.android.systemui") return
        val now = System.currentTimeMillis()
        if (now - lastPull < 800) return
        lastPull = now
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() {}
}
