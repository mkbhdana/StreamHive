package com.mkbhdana.streamhive.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Manages multiple top-level back stacks, one per tab.
 *
 * Adapted from the Navigation 3 common-ui recipe.
 * Each top-level route (Home, Folders, Search) has its own back stack.
 * Child routes (Settings, Player, etc.) are pushed onto the current tab's stack.
 * The exposed [backStack] is a flattened view of all stacks for use with NavDisplay.
 */
class TopLevelBackStack<T : Any>(startKey: T) {

    // Maintain a stack for each top level route
    private var topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> = linkedMapOf(
        startKey to mutableStateListOf(startKey)
    )

    // Expose the current top level route for consumers (nav bar selection)
    var topLevelKey by mutableStateOf(startKey)
        private set

    // Expose the back stack so it can be rendered by the NavDisplay
    val backStack: SnapshotStateList<T> = mutableStateListOf(startKey)

    /** The route currently shown (last entry in the flattened back stack). */
    val currentRoute: T? get() = backStack.lastOrNull()

    private fun updateBackStack() =
        backStack.apply {
            clear()
            addAll(topLevelStacks.flatMap { it.value })
        }

    /** Switch to a top-level tab (or create it if first visit). */
    fun addTopLevel(key: T) {
        if (topLevelStacks[key] == null) {
            topLevelStacks[key] = mutableStateListOf(key)
        } else {
            // Move to end so it's the active stack
            topLevelStacks.apply {
                remove(key)?.let { put(key, it) }
            }
        }
        topLevelKey = key
        updateBackStack()
    }

    /** Push a child route onto the current tab's stack. */
    fun add(key: T) {
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    /** Replace the current top entry on the active tab's stack. */
    fun replaceLast(key: T) {
        topLevelStacks[topLevelKey]?.let { stack ->
            if (stack.size > 1) {
                stack.removeLastOrNull()
            }
            stack.add(key)
        }
        updateBackStack()
    }

    /** Go back: pop the current entry, or pop the entire tab if at its root. */
    fun removeLast() {
        val removedKey = topLevelStacks[topLevelKey]?.removeLastOrNull()
        // If the removed key was a top level key, remove the associated stack
        topLevelStacks.remove(removedKey)
        topLevelKey = topLevelStacks.keys.last()
        updateBackStack()
    }

    /** Clear the entire current tab's stack and replace with the given route. */
    fun clearAndNavigate(key: T) {
        topLevelStacks.clear()
        topLevelStacks[key] = mutableStateListOf(key)
        topLevelKey = key
        updateBackStack()
    }
}
