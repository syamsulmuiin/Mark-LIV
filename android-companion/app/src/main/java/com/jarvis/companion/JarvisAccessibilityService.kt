package com.jarvis.companion

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-enabled Android UI bridge. Android itself displays the Accessibility
 * consent screen; the companion cannot silently enable this service.
 */
class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
        fun enabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    fun global(action: String): String {
        val a = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> error("Unsupported global action: $action")
        }
        if (!performGlobalAction(a)) error("Android rejected global action: $action")
        return "performed $action"
    }

    fun click(text: String, viewId: String = ""): String {
        val root = rootInActiveWindow ?: error("No active Android window")
        val matches = mutableListOf<AccessibilityNodeInfo>()
        if (viewId.isNotBlank()) matches += root.findAccessibilityNodeInfosByViewId(viewId)
        if (text.isNotBlank()) matches += root.findAccessibilityNodeInfosByText(text)
        val target = matches.firstOrNull() ?: error("UI element not found")
        var n: AccessibilityNodeInfo? = target
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "clicked"
            n = n.parent
        }
        error("UI element is not clickable")
    }

    fun setText(text: String, targetText: String = "", viewId: String = ""): String {
        val root = rootInActiveWindow ?: error("No active Android window")
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        if (viewId.isNotBlank()) candidates += root.findAccessibilityNodeInfosByViewId(viewId)
        if (targetText.isNotBlank()) candidates += root.findAccessibilityNodeInfosByText(targetText)
        if (candidates.isEmpty()) collectEditable(root, candidates)
        val node = candidates.firstOrNull { it.isEditable } ?: error("Editable field not found")
        val b = Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) error("Android rejected text input")
        return "text entered"
    }

    fun scroll(direction: String): String {
        val root = rootInActiveWindow ?: error("No active Android window")
        val scrollable = findFirst(root) { it.isScrollable } ?: error("No scrollable element found")
        val action = when (direction.lowercase()) {
            "up", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "down", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> error("Direction must be up/down/forward/backward")
        }
        if (!scrollable.performAction(action)) error("Android rejected scroll")
        return "scrolled $direction"
    }

    fun inspect(maxNodes: Int = 120): JSONObject {
        val root = rootInActiveWindow ?: error("No active Android window")
        val arr = JSONArray(); var count = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (count >= maxNodes) return
            val text = n.text?.toString().orEmpty(); val desc = n.contentDescription?.toString().orEmpty()
            val id = n.viewIdResourceName.orEmpty()
            if (text.isNotBlank() || desc.isNotBlank() || id.isNotBlank() || n.isClickable || n.isEditable) {
                arr.put(JSONObject().put("text", text).put("description", desc).put("view_id", id)
                    .put("class", n.className?.toString().orEmpty()).put("clickable", n.isClickable)
                    .put("editable", n.isEditable).put("scrollable", n.isScrollable).put("depth", depth))
                count++
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return JSONObject().put("package", root.packageName?.toString().orEmpty()).put("nodes", arr)
    }

    private fun collectEditable(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (n.isEditable) out += n
        for (i in 0 until n.childCount) n.getChild(i)?.let { collectEditable(it, out) }
    }
    private fun findFirst(n: AccessibilityNodeInfo, test: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
        if (test(n)) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { findFirst(it, test)?.let { found -> return found } }
        return null
    }
}
