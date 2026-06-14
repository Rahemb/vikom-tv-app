package com.example.tv_caller_app.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.tv_caller_app.R
import com.example.tv_caller_app.settings.SettingsManager
import com.example.tv_caller_app.ui.activities.MainActivity
import com.example.tv_caller_app.viewmodel.SettingsViewModel
import com.example.tv_caller_app.viewmodel.SettingsViewModelFactory

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireActivity().application)
    }

    private lateinit var settings: SettingsManager

    private lateinit var txtLanguageValue: TextView
    private lateinit var txtRingtoneValue: TextView
    private lateinit var txtAutoAnswerValue: TextView
    private lateinit var txtSessionInfo: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings = SettingsManager.getInstance(requireContext())

        txtLanguageValue = view.findViewById(R.id.txt_language_value)
        txtRingtoneValue = view.findViewById(R.id.txt_ringtone_value)
        txtAutoAnswerValue = view.findViewById(R.id.txt_auto_answer_value)
        txtSessionInfo = view.findViewById(R.id.txt_session_info)
        updateAllValues()

        viewModel.sessionInfo.observe(viewLifecycleOwner) { info ->
            txtSessionInfo.text = buildString {
                append("E-post: ${info["email"]}\n")
                append("Bekreftet: ${if (info["emailConfirmed"] == "true") "Ja" else "Nei"}\n")
                append("Økt alder: ${info["sessionAge"]}")
            }
        }
        viewModel.loadSessionInfo()

        view.findViewById<LinearLayout>(R.id.setting_language).setOnClickListener {
            showLanguageDialog()
        }

        view.findViewById<LinearLayout>(R.id.setting_ringtone).setOnClickListener {
            settings.ringtoneEnabled = !settings.ringtoneEnabled
            updateRingtoneValue()
        }

        view.findViewById<LinearLayout>(R.id.setting_auto_answer).setOnClickListener {
            settings.autoAnswer = !settings.autoAnswer
            updateAutoAnswerValue()
        }

        view.findViewById<TextView>(R.id.btn_go_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        (activity as? MainActivity)?.hideHeader()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.showHeader()
    }

    private fun updateAllValues() {
        updateLanguageValue()
        updateRingtoneValue()
        updateAutoAnswerValue()
    }

    private fun updateRingtoneValue() {
        txtRingtoneValue.text = if (settings.ringtoneEnabled) {
            getString(R.string.setting_on)
        } else {
            getString(R.string.setting_off)
        }
    }

    private fun updateLanguageValue() {
        txtLanguageValue.text = when (settings.language) {
            "en" -> "English"
            else -> "Norsk"
        }
    }

    private fun updateAutoAnswerValue() {
        txtAutoAnswerValue.text = if (settings.autoAnswer) {
            getString(R.string.setting_on)
        } else {
            getString(R.string.setting_off)
        }
    }

    private fun showLanguageDialog() {
        val options = arrayOf("Norsk", "English")
        val currentIndex = if (settings.language == "en") 1 else 0

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_language))
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val newLang = if (which == 1) "en" else "nb"
                if (newLang != settings.language) {
                    settings.language = newLang
                    dialog.dismiss()
                    activity?.recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
