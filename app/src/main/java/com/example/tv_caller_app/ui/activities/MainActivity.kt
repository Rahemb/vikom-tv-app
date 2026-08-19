package com.example.tv_caller_app.ui.activities

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.repository.PatientProfileRepository
import kotlinx.coroutines.launch
import com.example.tv_caller_app.ui.fragments.AppointmentListFragment
import com.example.tv_caller_app.ui.fragments.AppointmentsFragment
import com.example.tv_caller_app.R
import com.example.tv_caller_app.ui.fragments.HamburgerMenuFragment
import com.example.tv_caller_app.ui.fragments.ProfileFragment
import com.example.tv_caller_app.ui.fragments.QuickDialFragment
import com.example.tv_caller_app.ui.fragments.SettingsFragment
import com.example.tv_caller_app.settings.SettingsManager
import com.example.tv_caller_app.viewmodel.AuthViewModel
import com.example.tv_caller_app.viewmodel.AuthViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_APPOINTMENTS = "appointments"

        /**
         * Lands on the Avtaler tab and immediately opens the full list on top of
         * it. Used by the notification popup, whose button says "Se alle avtaler"
         * — showing only the next appointment there would not honour the label.
         */
        const val TAB_APPOINTMENTS_LIST = "appointments_list"

        /**
         * Opens MainActivity, optionally landing on a specific tab. Used by
         * AppointmentActivity's "Se alle avtaler" button.
         */
        fun createIntent(context: Context, openTab: String? = null): Intent {
            return Intent(context, MainActivity::class.java).apply {
                openTab?.let { putExtra(EXTRA_OPEN_TAB, it) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private lateinit var btnLogout: LinearLayout
    private lateinit var headerBar: RelativeLayout
    private lateinit var txtWelcome: TextView
    private lateinit var txtClockTime: TextView
    private lateinit var txtClockDate: TextView
    private lateinit var authViewModel: AuthViewModel

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 30000) 
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLocale()
        setContentView(R.layout.activity_main)

        btnLogout = findViewById(R.id.btn_logout)
        headerBar = findViewById(R.id.header_bar)
        txtWelcome = findViewById(R.id.txt_welcome)
        txtClockTime = findViewById(R.id.txt_clock_time)
        txtClockDate = findViewById(R.id.txt_clock_date)

        authViewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(application)
        )[AuthViewModel::class.java]

        observeAuthViewModel(savedInstanceState)
        setupLogoutButton()
        setupHamburgerMenu()
        setupProfileButton()
        setupSettingsButton()
        applyMenuLabelVisibility()
        updateClock()
        clockHandler.postDelayed(clockRunnable, 30000)
        authViewModel.checkSession()
    }

    /**
     * Greets the patient by their real name.
     *
     * The Supabase profile only carries a handle ("ingrid.berg"), which is no way to
     * address someone in their own living room, so the backend is asked for the full
     * name. The handle is shown immediately and replaced when the call returns —
     * a header that is briefly less personal beats one that is briefly empty, and a
     * patient with no linked record keeps the handle rather than nothing.
     */
    private fun showGreeting(username: String) {
        txtWelcome.text = getString(R.string.greeting_format, username)

        lifecycleScope.launch {
            val fullName = PatientProfileRepository
                .getInstance(SessionManager.getInstance(this@MainActivity))
                .fullNameOrNull()

            if (!fullName.isNullOrBlank()) {
                txtWelcome.text = getString(R.string.greeting_format, fullName)
            }
        }
    }

    private fun applyLocale() {
        val lang = SettingsManager.getInstance(this).language
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun updateClock() {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("nb-NO"))
        txtClockTime.text = timeFormat.format(now)
        txtClockDate.text = dateFormat.format(now)
    }

    

    private fun loadMainContent(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            val requestedTab = intent?.getStringExtra(EXTRA_OPEN_TAB)
            val wantsAppointments =
                requestedTab == TAB_APPOINTMENTS || requestedTab == TAB_APPOINTMENTS_LIST

            val fragment = if (wantsAppointments) AppointmentsFragment() else QuickDialFragment()

            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .commitNow()

            // Pushed after the tab so BACK from the list lands on the hero
            // rather than leaving the app.
            if (requestedTab == TAB_APPOINTMENTS_LIST) {
                AppointmentListFragment.open(this)
            }
        }
    }

    /**
     * createIntent uses CLEAR_TOP | SINGLE_TOP, so when MainActivity is already
     * running onCreate does not run again and loadMainContent is never reached.
     * Without this override the "Se alle avtaler" button would appear to do
     * nothing.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_APPOINTMENTS -> showAppointmentsTab(openFullList = false)
            TAB_APPOINTMENTS_LIST -> showAppointmentsTab(openFullList = true)
        }
    }

    private fun showAppointmentsTab(openFullList: Boolean) {
        // Any list pushed by an earlier visit is cleared first, so arriving twice
        // cannot stack two lists on the back stack.
        supportFragmentManager.popBackStackImmediate(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, AppointmentsFragment())
            .runOnCommit {
                findViewById<android.widget.FrameLayout>(R.id.main_browse_fragment)?.post {
                    findViewById<TextView>(R.id.tab_appointments)?.requestFocus()
                }
            }
            .commit()

        if (openFullList) {
            AppointmentListFragment.open(this)
        }
    }

    private fun observeIncomingCalls() {
        val app = application as com.example.tv_caller_app.TVCallerApplication
        val callViewModel = app.getCallViewModel()

        callViewModel?.callState?.observe(this) { state ->
            Log.d(TAG, "Call state changed: $state")

            when (state) {
                is com.example.tv_caller_app.viewmodel.CallViewModel.CallState.RingingIncoming -> {
                    Log.i(TAG, "Incoming call detected - launching IncomingCallActivity")
                    val intent = IncomingCallActivity.createIntent(
                        context = this,
                        callerId = state.callerId,
                        callerName = state.callerName,
                        callId = state.callerId + "_" + System.currentTimeMillis(),
                        callerPhone = null,
                        nickname = state.nickname,
                        avatarUrl = state.avatarUrl
                    )
                    startActivity(intent)
                }
                is com.example.tv_caller_app.viewmodel.CallViewModel.CallState.RingingOutgoing -> {
                    Log.i(TAG, "Outgoing call initiated - launching OutgoingCallActivity")
                    val intent = OutgoingCallActivity.createIntent(
                        context = this,
                        contactId = state.targetUserId,
                        contactName = state.contactName,
                        callId = state.targetUserId + "_" + System.currentTimeMillis(),
                        contactPhone = null,
                        nickname = state.nickname,
                        avatarUrl = state.avatarUrl
                    )
                    startActivity(intent)
                }
                else -> {
                    Log.d(TAG, "Call state: $state (no action needed in MainActivity)")
                }
            }
        }
    }

    private fun redirectToAuth() {
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupHamburgerMenu() {
        val isLargeScreen = resources.getBoolean(R.bool.is_large_screen)
        val isTv = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        if (!isLargeScreen && !isTv) {
            val iconsContainer = findViewById<LinearLayout>(R.id.icons_container)
            val hamburgerContainer = findViewById<LinearLayout>(R.id.btn_hamburger_container)
            val btnHamburger = findViewById<ImageView>(R.id.btn_hamburger)
            val clockSpacer = findViewById<View>(R.id.clock_spacer)
            val clockContainer = findViewById<LinearLayout>(R.id.clock_container)

            iconsContainer.visibility = View.GONE
            clockSpacer.visibility = View.GONE
            clockContainer.visibility = View.GONE
            hamburgerContainer.visibility = View.VISIBLE

            btnHamburger.setOnClickListener {
                Log.d(TAG, "Hamburger menu clicked")
                HamburgerMenuFragment().show(supportFragmentManager, "hamburger_menu")
            }
        }
    }

    private fun setupProfileButton() {
        findViewById<LinearLayout>(R.id.btn_profile)?.setOnClickListener {
            Log.d(TAG, "Profile button clicked")
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupSettingsButton() {
        findViewById<LinearLayout>(R.id.btn_settings)?.setOnClickListener {
            Log.d(TAG, "Settings button clicked")
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun applyMenuLabelVisibility() {
        val isTv = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_TELEVISION
        val visibility = if (isTv) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txt_logout_label)?.visibility = visibility
        findViewById<TextView>(R.id.txt_settings_label)?.visibility = visibility
        findViewById<TextView>(R.id.txt_profile_label)?.visibility = visibility
    }

    private fun observeAuthViewModel(savedInstanceState: Bundle?) {
        authViewModel.sessionValid.observe(this) { isValid ->
            when (isValid) {
                true -> {
                    Log.d(TAG, "Valid session - loading main content")
                    authViewModel.cachedUsername.observe(this) { username ->
                        if (!username.isNullOrBlank()) {
                            showGreeting(username)
                        }
                    }
                    val app = application as com.example.tv_caller_app.TVCallerApplication
                    app.startPresence("tv")
                    app.initializeCallViewModel()
                    com.example.tv_caller_app.calling.service.SignalingForegroundService.startService(this)
                    observeIncomingCalls()
                    loadMainContent(savedInstanceState)
                }
                false -> {
                    Log.d(TAG, "No valid session - redirecting to AuthActivity")
                    redirectToAuth()
                }
                null -> {} 
            }
        }

        authViewModel.loggedOut.observe(this) { loggedOut ->
            if (loggedOut) redirectToAuth()
        }

        authViewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                authViewModel.clearError()
            }
        }
    }

    private fun setupLogoutButton() {
        btnLogout.setOnClickListener {
            Log.d(TAG, "Logout button clicked - showing confirmation")
            showLogoutConfirmDialog()
        }
    }

    fun showLogoutConfirmDialog() {
        showConfirmDialog(
            title = "Utloggingsbekreftelse",
            message = "Er du sikker på at du vil logge ut ?",
            onConfirm = { performLogout() }
        )
    }

    /**
     * @param icon a drawable, not a glyph: emoji render differently on every
     *   launcher skin and cannot be tinted to the brand palette.
     */
    fun showConfirmDialog(
        title: String,
        message: String,
        @DrawableRes icon: Int = R.drawable.ic_warning,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_logout_confirm)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        dialog.findViewById<ImageView>(R.id.dialog_icon).setImageResource(icon)
        dialog.findViewById<TextView>(R.id.dialog_title).text = title
        dialog.findViewById<TextView>(R.id.dialog_message).text = message

        val btnConfirm = dialog.findViewById<TextView>(R.id.btn_confirm)
        val btnCancel = dialog.findViewById<TextView>(R.id.btn_cancel)

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        btnCancel.requestFocus()
    }

    private fun performLogout() {
        Log.d(TAG, "Performing logout")
        authViewModel.logout()
    }

    

    fun showHeader() {
        headerBar.visibility = View.VISIBLE
    }

    

    fun hideHeader() {
        headerBar.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        checkForActiveCall()
    }

    private fun checkForActiveCall() {
        val app = application as? com.example.tv_caller_app.TVCallerApplication ?: return
        val callViewModel = app.getCallViewModel() ?: return

        when (val state = callViewModel.callState.value) {
            is com.example.tv_caller_app.viewmodel.CallViewModel.CallState.Connected -> {
                Log.i(TAG, "Active call detected on resume - relaunching InCallActivity")
                val intent = InCallActivity.createIntent(
                    context = this,
                    contactId = state.remoteUserId,
                    contactName = state.remoteUserName,
                    callId = state.remoteUserId,
                    isIncoming = state.isIncoming,
                    nickname = state.nickname,
                    avatarUrl = state.avatarUrl
                )
                startActivity(intent)
            }
            is com.example.tv_caller_app.viewmodel.CallViewModel.CallState.Connecting -> {
                Log.i(TAG, "Connecting call detected on resume - relaunching InCallActivity")
                val intent = InCallActivity.createIntent(
                    context = this,
                    contactId = callViewModel.getRemoteUserId() ?: "",
                    contactName = callViewModel.getRemoteUserName() ?: "Unknown",
                    callId = callViewModel.getRemoteUserId() ?: "",
                    isIncoming = callViewModel.isIncomingCall()
                )
                startActivity(intent)
            }
            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        Log.d(TAG, "MainActivity being destroyed")

        if (isFinishing) {
            Log.d(TAG, "Activity finishing - setting user offline")
            val app = application as com.example.tv_caller_app.TVCallerApplication
            app.stopPresence()
        }
    }
}
