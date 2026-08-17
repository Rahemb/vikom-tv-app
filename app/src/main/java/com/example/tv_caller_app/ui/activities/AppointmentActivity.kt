package com.example.tv_caller_app.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.tv_caller_app.R
import com.example.tv_caller_app.settings.SettingsManager
import com.example.tv_caller_app.ui.util.AppointmentStatusUi
import java.util.Locale

class AppointmentActivity : FragmentActivity() {

    companion object {
        private const val TAG = "AppointmentActivity"
        private const val EXTRA_APPOINTMENT_ID = "appointment_id"
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_START_TIME = "start_time"
        private const val EXTRA_END_TIME = "end_time"
        private const val EXTRA_PERSONNEL_NAME = "personnel_name"
        private const val EXTRA_STATUS = "status"
        private const val EXTRA_SHORT_MESSAGE = "short_message"

        fun createIntent(
            context: Context,
            appointmentId: Int,
            action: String,
            date: String,
            startTime: String,
            endTime: String,
            personnelName: String,
            status: String,
            shortMessage: String
        ): Intent {
            return Intent(context, AppointmentActivity::class.java).apply {
                putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
                putExtra(EXTRA_ACTION, action)
                putExtra(EXTRA_DATE, date)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_END_TIME, endTime)
                putExtra(EXTRA_PERSONNEL_NAME, personnelName)
                putExtra(EXTRA_STATUS, status)
                putExtra(EXTRA_SHORT_MESSAGE, shortMessage)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    private var appointmentId: Int = 0
    private var action: String = ""
    private var date: String = ""
    private var startTime: String = ""
    private var endTime: String = ""
    private var personnelName: String = ""
    private var status: String = ""
    private var shortMessage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLocale()
        setContentView(R.layout.activity_appointment)

        extractIntentData()
        initializeViews()
    }

    /**
     * Applies the language chosen in Settings, as the other activities do.
     *
     * Without this the screen inherits the device locale, which showed the date as
     * "Thursday 20. August" next to otherwise Norwegian labels — the date is
     * formatted with Locale.getDefault(), and only applyLocale() sets that.
     */
    private fun applyLocale() {
        val lang = SettingsManager.getInstance(this).language
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun extractIntentData() {
        appointmentId = intent.getIntExtra(EXTRA_APPOINTMENT_ID, 0)
        action = intent.getStringExtra(EXTRA_ACTION) ?: ""
        date = intent.getStringExtra(EXTRA_DATE) ?: ""
        startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""
        endTime = intent.getStringExtra(EXTRA_END_TIME) ?: ""
        personnelName = intent.getStringExtra(EXTRA_PERSONNEL_NAME) ?: ""
        status = intent.getStringExtra(EXTRA_STATUS) ?: ""
        shortMessage = intent.getStringExtra(EXTRA_SHORT_MESSAGE) ?: ""

        Log.d(TAG, "Appointment loaded: id=$appointmentId action=$action date=$date $startTime-$endTime by $personnelName")
    }

    private fun initializeViews() {
        val txtAction = findViewById<TextView>(R.id.txt_appointment_action)
        val txtStatus = findViewById<TextView>(R.id.txt_appointment_status)
        val txtMessage = findViewById<TextView>(R.id.txt_appointment_message)
        val txtDate = findViewById<TextView>(R.id.txt_appointment_date)
        val txtTime = findViewById<TextView>(R.id.txt_appointment_time)
        val txtPersonnel = findViewById<TextView>(R.id.txt_appointment_personnel)
        val btnSeeAll = findViewById<TextView>(R.id.btn_appointment_see_all)
        val btnClose = findViewById<TextView>(R.id.btn_appointment_close)

        // Shares its status/date formatting with the appointments list so the two
        // screens cannot describe the same appointment differently.
        txtAction.setText(AppointmentStatusUi.actionLabelRes(action))
        txtStatus.setText(AppointmentStatusUi.labelRes(status))
        txtStatus.setBackgroundResource(AppointmentStatusUi.chipRes(status))

        txtDate.text = AppointmentStatusUi.formatLongDate(date)
        txtTime.text = getString(R.string.appointment_time_format, startTime, endTime)

        txtPersonnel.text = if (personnelName.isBlank()) {
            ""
        } else {
            getString(R.string.appointment_personnel_format, personnelName)
        }
        txtPersonnel.visibility = if (personnelName.isBlank()) View.GONE else View.VISIBLE

        txtMessage.text = shortMessage
        txtMessage.visibility = if (shortMessage.isBlank()) View.GONE else View.VISIBLE

        btnSeeAll.setOnClickListener {
            startActivity(MainActivity.createIntent(this, MainActivity.TAB_APPOINTMENTS))
            finish()
        }

        btnClose.setOnClickListener { finish() }

        // Focus the dismissive action, matching the confirm dialog elsewhere, so a
        // stray D-pad press cannot navigate somewhere unexpected.
        btnClose.requestFocus()
    }
}
