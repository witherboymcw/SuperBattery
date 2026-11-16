package com.witherboymcw.superbattery.ui

import android.view.View

/**
 * Minimal interface that [DrawerScreen] needs from the hosting Activity.
 * Implemented by MainActivity to avoid a hard dependency on the concrete class
 * inside the ui package (breaking the awareness ↔ ui cycle).
 */
interface SettingsHost {
    /** Returns the view with the given id, or null if not found. */
    fun <T : View> findView(id: Int): T?

    /** The settings/gear button view that rotates when the settings drawer opens. */
    val settingsActionButton: View?
}
