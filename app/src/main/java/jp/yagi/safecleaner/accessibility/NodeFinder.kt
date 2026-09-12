package jp.yagi.safecleaner.accessibility

import android.view.accessibility.AccessibilityNodeInfo

internal object NodeFinder {
    fun findByResourceSuffix(root: AccessibilityNodeInfo?, suffixes: Set<String>): AccessibilityNodeInfo? =
        walk(root).firstOrNull { node ->
            val id = node.viewIdResourceName?.substringAfterLast('/') ?: return@firstOrNull false
            id in suffixes && node.isVisibleToUser
        }

    fun findByExactLabel(root: AccessibilityNodeInfo?, labels: Set<String>): AccessibilityNodeInfo? =
        walk(root).firstOrNull { node ->
            val label = node.text?.toString()?.trim().orEmpty()
                .ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
            label in labels && node.isVisibleToUser
        }

    fun containsAnyLabel(root: AccessibilityNodeInfo?, labels: Set<String>): Boolean =
        walk(root).any { node ->
            node.text?.toString()?.trim() in labels ||
                node.contentDescription?.toString()?.trim() in labels
        }

    fun clickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        repeat(5) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    fun firstScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        walk(root).firstOrNull { it.isVisibleToUser && it.isScrollable }

    private fun walk(root: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (root == null) return@sequence
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            yield(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
    }
}
