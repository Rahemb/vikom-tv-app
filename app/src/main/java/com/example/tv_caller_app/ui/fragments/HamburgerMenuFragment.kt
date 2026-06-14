package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.tv_caller_app.R
import com.example.tv_caller_app.ui.activities.MainActivity

class HamburgerMenuFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_hamburger_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_close_menu).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.menu_item_profile).setOnClickListener {
            dismiss()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.menu_item_settings).setOnClickListener {
            dismiss()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.menu_item_logout).setOnClickListener {
            dismiss()
            (activity as? MainActivity)?.showLogoutConfirmDialog()
        }
    }
}
