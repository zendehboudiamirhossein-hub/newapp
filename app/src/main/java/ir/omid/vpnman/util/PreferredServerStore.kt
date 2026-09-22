package ir.omid.vpnman.util

import android.content.Context

/**
 * Remembers the ID of the last server auto-selection actually picked as "best", so the
 * next app launch can preselect it immediately instead of showing "در حال بررسی…" until
 * a fresh round of latency probes finishes. This is only a fast-start guess: the live
 * probe still runs right after startup as before and overrides it the moment it has a
 * real answer, so a stale pick never lingers for more than a moment.
 */
object PreferredServerStore {
    private const val PREFS = "vpn_man_preferred"
    private const val KEY_SERVER_ID = "last_best_server_id"

    fun save(context: Context, serverId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SERVER_ID, serverId).apply()
    }

    fun load(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVER_ID, null)
}
