package com.example.tv_caller_app.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.tv_caller_app.R

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
        setContentView(R.layout.activity_appointment)

        extractIntentData()
        initializeViews()
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
        val txtMessage = findViewById<TextView>(R.id.txt_appointment_message)
        val txtDate = findViewById<TextView>(R.id.txt_appointment_date)
        val txtTime = findViewById<TextView>(R.id.txt_appointment_time)
        val txtPersonnel = findViewById<TextView>(R.id.txt_appointment_personnel)
        val btnOk = findViewById<Button>(R.id.btn_appointment_ok)

        txtMessage.text = if (shortMessage.isNotBlank()) shortMessage else getString(R.string.appointment_title_default)
        txtDate.text = date
        txtTime.text = getString(R.string.appointment_time_format, startTime, endTime)
        txtPersonnel.text = personnelName

        btnOk.setOnClickListener { finish() }
    }
}
