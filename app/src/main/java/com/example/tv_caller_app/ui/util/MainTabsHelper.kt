package com.example.tv_caller_app.ui.util

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.example.tv_caller_app.R
import com.example.tv_caller_app.ui.fragments.AllContactsFragment
import com.example.tv_caller_app.ui.fragments.AppointmentsFragment
import com.example.tv_caller_app.ui.fragments.QuickDialFragment

/**
 * Owns the shared tab strip in view_main_tabs.xml: which tab looks selected, and
 * what happens when another one is pressed.
 *
 * Previously each fragment duplicated this transaction code, so adding a third
 * tab would have meant a third copy. A plain object here follows the same
 * convention as [AvatarHelper].
 */
object MainTabsHelper {

    enum class Tab { QUICK_DIAL, CONTACTS, APPOINTMENTS }

    /**
     * Styles the strip inside [root] for [active] and wires the other two tabs to
     * swap the hosted fragment.
     */
    fun bind(root: View, active: Tab) {
        Tab.entries.forEach { tab ->
            val view = root.findViewById<TextView>(tabViewId(tab)) ?: return@forEach

            view.setBackgroundResource(
                if (tab == active) R.drawable.tab_button_active
                else R.drawable.tab_button_inactive
            )

            if (tab == active) {
                // Consume the click so it cannot fall through to anything behind,
                // matching the empty listeners the fragments used to set.
                view.setOnClickListener { }
            } else {
                view.setOnClickListener { showTab(root, tab) }
            }
        }
    }

    /**
     * Replaces the hosted fragment and moves focus onto the tab that was pressed.
     *
     * No addToBackStack, so the three tabs stay peers and Back leaves the app
     * rather than cycling through previously visited tabs — the behaviour the two
     * original fragments already had.
     */
    private fun showTab(root: View, target: Tab) {
        val activity = root.context as? FragmentActivity ?: return

        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, newFragment(target))
            .runOnCommit {
                // Posting after commit lets the new fragment's views exist before
                // focus is requested; without it the D-pad loses its place.
                activity.findViewById<FrameLayout>(R.id.main_browse_fragment)?.post {
                    activity.findViewById<TextView>(tabViewId(target))?.requestFocus()
                }
            }
            .commit()
    }

    private fun newFragment(tab: Tab): Fragment = when (tab) {
        Tab.QUICK_DIAL -> QuickDialFragment()
        Tab.CONTACTS -> AllContactsFragment()
        Tab.APPOINTMENTS -> AppointmentsFragment()
    }

    fun tabViewId(tab: Tab): Int = when (tab) {
        Tab.QUICK_DIAL -> R.id.tab_quick_dial
        Tab.CONTACTS -> R.id.tab_contacts
        Tab.APPOINTMENTS -> R.id.tab_appointments
    }
}
