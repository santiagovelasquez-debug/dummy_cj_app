package com.appbase

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.appbase.usbhid.UsbHid
import com.appbase.ui.subscription.SubscriptionUiState
import com.appbase.ui.subscription.SubscriptionViewModel
import com.appbase.util.DeviceIdProvider
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.appbase.util.launchExternalApp  // Add this import

class SplashActivity : ComponentActivity() {

    private val SPLASH_TIME_OUT: Long = 1000 // 2 seconds
    
    // Vendor ID and Product ID for the CJ Clip device
    private val CJ_CLIP_VID = 0x0483 // Replace with actual VID
    private val CJ_CLIP_PID = 0x5750 // Replace with actual PID
    
    private val subscriptionViewModel: SubscriptionViewModel by viewModel()
    private val deviceId: String by lazy { DeviceIdProvider.getDeviceId(this) }
    private val demoDeviceId = "CJ9Mxbf111111"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val tvVersion: TextView = findViewById(R.id.tvVersion)
    
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            tvVersion.text = "Version $version"
        } catch (e: Exception) {
            e.printStackTrace()
            tvVersion.text = "Version 1.0"
        }



        // Observe subscription state for automatic navigation
        //observeSubscriptionState()

        Handler(Looper.getMainLooper()).postDelayed({
            //checkCjClipAndProceed()
            startActivity(Intent(this, DemoActivity::class.java))
            finish()
        }, SPLASH_TIME_OUT)
    }

    /**
     * Checks if CJ Clip is connected. If not, verifies subscription.
     * If subscription is granted, launches external app directly.
     */
    private fun checkCjClipAndProceed() {
        if (isCjClipConnected()) {
            // CJ Clip is connected - go to selection activity
            navigateToSelection()
        } else {
            // CJ Clip not connected - check subscription
            subscriptionViewModel.verifyAccess(demoDeviceId)//deviceId)
        }
    }

    /**
     * Checks if the CJ Clip USB HID device is connected.
     */
    private fun isCjClipConnected(): Boolean {
        return try {
            val device = UsbHidDevice.factory(this, CJ_CLIP_VID, CJ_CLIP_PID)
            device != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Observes subscription verification results.
     */
    private fun observeSubscriptionState() {
        lifecycleScope.launch {
            subscriptionViewModel.uiState.collect { state ->
                when (state) {
                    is SubscriptionUiState.AccessGranted -> {
                        StateProvider.pin_drive_status = true
                        launchExternalApp(
                            packageName = "com.diag.scan",
                            finishAfterLaunch = true,
                            onAppNotFound = { navigateToSelection() }
                        )
                    }
                    is SubscriptionUiState.NoSubscription -> {
                        showNoSubscriptionDialog()
                    }
                    is SubscriptionUiState.Expired -> {
                        showExpiredDialog(state.expiredAt)
                    }
                    is SubscriptionUiState.Error -> {
                        showErrorDialog(state.message)
                    }
                    else -> {
                    }
                }
            }
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    /**
     * Navigates to the SelectionActivity and finishes current activity.
     */
    private fun navigateToSelection() {
        startActivity(Intent(this, SelectionActivity::class.java))
        finish()
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    /**
     * Displays a dialog when no subscription is found for this device.
     * Offers the user options to subscribe or cancel.
     */
    private fun showNoSubscriptionDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Sin Licencia Activada")
            .setMessage("No se detectó CJ Clip conectado y no cuenta con licencia vigente para este dispositivo.\n\n¿Deseas obtener una licencia ahora?")
            .setPositiveButton("Obtener licencia") { _, _ ->
                StateProvider.pin_drive_status = false
                subscriptionViewModel.openPaymentFlow(this, deviceId)
                subscriptionViewModel.resetState()
                finish()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                subscriptionViewModel.resetState()
                navigateToSelection()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Displays a dialog when the subscription has expired.
     * Shows the expiration date if available and offers renewal options.
     *
     * @param expiredAt ISO date string of when the subscription expired (nullable)
     */
    private fun showExpiredDialog(expiredAt: String?) {
        val expiredMessage = if (expiredAt != null) "\n\nVenció el: $expiredAt" else ""
        android.app.AlertDialog.Builder(this)
            .setTitle("Suscripción Vencida")
            .setMessage("No se detectó CJ Clip conectado y tu suscripción ha expirado.$expiredMessage\n\n¿Deseas renovarla ahora?")
            .setPositiveButton("Renovar") { _, _ ->
                StateProvider.pin_drive_status = false
                subscriptionViewModel.openPaymentFlow(this, deviceId)
                subscriptionViewModel.resetState()
                finish()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                subscriptionViewModel.resetState()
                navigateToSelection()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Displays an error dialog when subscription verification fails.
     * This typically occurs due to network issues or server errors.
     *
     * @param message Error description from the ViewModel
     */
    private fun showErrorDialog(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Error de Verificación")
            .setMessage("No se detectó CJ Clip conectado y no se pudo verificar la suscripción.\n\n$message\n\n¿Deseas intentar suscribirte?")
            .setPositiveButton("Suscribirse") { _, _ ->
                StateProvider.pin_drive_status = false
                subscriptionViewModel.openPaymentFlow(this, deviceId)
                subscriptionViewModel.resetState()
                finish()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                subscriptionViewModel.resetState()
                navigateToSelection()
            }
            .setCancelable(false)
            .show()
    }
}
