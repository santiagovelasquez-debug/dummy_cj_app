package com.appbase

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.appbase.ui.subscription.SubscriptionUiState
import com.appbase.ui.subscription.SubscriptionViewModel
import com.appbase.util.launchExternalApp
import com.google.android.material.textfield.TextInputEditText
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.view.LayoutInflater
import androidx.core.content.ContentProviderCompat.requireContext
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import android.database.Cursor
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
/**
 * Demo activity that allows manual entry of a Device ID to verify subscription status.
 * The user inputs a device ID manually via an EditText, and the app verifies
 * if that device ID has a valid subscription via the same API used in SelectionActivity.
 */
@Suppress("DEPRECATION")
class DemoActivity : ComponentActivity() {

    private lateinit var etDeviceId: TextInputEditText
    private lateinit var btnVerifySubscription: Button
    private lateinit var btnReadSerialNumber: Button
    private lateinit var texttvSN: TextView

    /**
     * ViewModel injected by Koin dependency injection framework.
     * Handles subscription verification logic and maintains UI state.
     */
    private val subscriptionViewModel: SubscriptionViewModel by viewModel()

    /**
     * The device ID entered by the user in the EditText.
     */
    private var manualDeviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        if (!isInternetAvailable(this)) {

        } else {
            //Use token info to validate the device
            showErrorDialog2()
        }
        initializeViews()
        setupClickListeners()
        observeSubscriptionState()
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }
    private fun initializeViews() {
        etDeviceId = findViewById(R.id.etDeviceId)
        btnVerifySubscription = findViewById(R.id.btnVerifySubscription)
        btnReadSerialNumber= findViewById(R.id.button_get_sn)
        texttvSN = findViewById(R.id.tv_sn)
    }

    private fun setupClickListeners() {
        // Trigger subscription verification via REST API using manually entered device ID
        btnVerifySubscription.setOnClickListener {
            verifyLicense()
        }

        // Trigger verification when pressing "Done" on the keyboard
        etDeviceId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verifyLicense()
                true
            } else {
                false
            }
        }

        btnReadSerialNumber.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {

                texttvSN.text = "Reading serial number, please wait..."

                val serialNumber = getVCISerialNumber()

                if (serialNumber.isNullOrEmpty()) {
                    texttvSN.text = "CJ9PRO app unbound VCI, "
                } else {
                    texttvSN.text = serialNumber
                }
            }
        }
    }
    private suspend fun getVCISerialNumber() = withContext(Dispatchers.IO) {
        val cursor: Cursor? = contentResolver.query(
            Uri.parse("content://com.diag.scan/vci"),
            null, null, null, null
        )

        if (cursor != null && cursor.moveToFirst()) {
            val serialNumber = cursor.getString(cursor.getColumnIndexOrThrow("serialNumber"))
            cursor.close()
            serialNumber
        } else {
            null
        }
    }

    private fun verifyLicense() {

        val suffix = etDeviceId.text?.toString()?.trim() ?: ""

        if (suffix.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa el número de serie", Toast.LENGTH_SHORT).show()
            return
        }

        // Always prepend the fixed prefix
//        manualDeviceId = "CJ9MXbf$suffix"
        manualDeviceId = "$suffix"

        // Verify subscription with the full device ID
        subscriptionViewModel.verifyAccess(manualDeviceId)

//        manualDeviceId = etDeviceId.text?.toString()?.trim() ?: ""
//
//        if (manualDeviceId.isEmpty()) {
//            Toast.makeText(this, "Por favor ingresa un Device ID", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Verify subscription with the manually entered device ID
//        subscriptionViewModel.verifyAccess(manualDeviceId)
    }

    /**
     * Sets up a coroutine-based observer for subscription state changes.
     * Uses lifecycleScope to automatically cancel collection when activity is destroyed.
     *
     * Reacts to the following states from SubscriptionViewModel:
     * - Idle: Initial state, no action needed
     * - Loading: API call in progress, disable button and show loading text
     * - AccessGranted: Valid subscription, show success dialog and allow app launch
     * - NoSubscription: Device not registered, offer subscription purchase
     * - Expired: Subscription ended, offer renewal
     * - Error: Network/server failure, offer retry option
     */
    private fun observeSubscriptionState() {
        lifecycleScope.launch {
            subscriptionViewModel.uiState.collect { state ->
                when (state) {

                    is SubscriptionUiState.Idle -> {
                        // Initial state - no action required
                    }

                    is SubscriptionUiState.Loading -> {
                        // Disable button during API call to prevent duplicate requests
                        btnVerifySubscription.isEnabled = false
                        btnVerifySubscription.text = "Validando..."
                    }

                    is SubscriptionUiState.AccessGranted -> {
                        // Re-enable button and restore label
                        btnVerifySubscription.isEnabled = true
                        btnVerifySubscription.text = "Verificar Licencia"
                        // ✅ Active subscription — show details and offer to launch external app
                        showAccessGrantedDialog(state.plan, state.remainingDays, state.message)
                    }

                    is SubscriptionUiState.NoSubscription -> {
                        btnVerifySubscription.isEnabled = true
                        btnVerifySubscription.text = "Verificar Licencia"
                        // ❌ No subscription found — prompt user to subscribe
                        showNoSubscriptionDialog()
                    }

                    is SubscriptionUiState.Expired -> {
                        btnVerifySubscription.isEnabled = true
                        btnVerifySubscription.text = "Verificar Licencia"
                        // ❌ Subscription expired — prompt user to renew
                        showExpiredDialog(state.expiredAt)
                    }

                    is SubscriptionUiState.Error -> {
                        btnVerifySubscription.isEnabled = true
                        btnVerifySubscription.text = "Verificar Licencia"
                        // ⚠️ Network or server error — show error with retry option
                        showErrorDialog(state.message)
                    }
                }
            }
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    /**
     * Displays a success dialog when the device has an active subscription.
     * Shows subscription details (plan name, remaining days) and allows
     * the user to proceed to the external dependent application.
     *
     * @param plan The subscription plan name (e.g., "Premium", "Basic")
     * @param remainingDays Number of days until subscription expires
     * @param message Server-provided confirmation message
     */
    private fun showAccessGrantedDialog(plan: String, remainingDays: Int, message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("✅ Licencia Vigente")
            .setIcon(R.drawable.background_status_active)
            .setMessage("Numero de Serie: $manualDeviceId\n\n$message\n\nPlan: $plan\n\n")
            .setPositiveButton("Continuar") { _, _ ->
                StateProvider.pin_drive_status = true
                launchExternalApp("com.diag.scan")
                subscriptionViewModel.resetState()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Displays a dialog when no subscription is found for this device.
     * Offers the user two options:
     * - Subscribe: Opens the payment flow to purchase a subscription
     * - Cancel: Dismisses the dialog and returns to selection screen
     */
    private fun showNoSubscriptionDialog() {

        android.app.AlertDialog.Builder(this)
            .setTitle("CJ9 requiere una Licencia")
            .setIcon(R.drawable.background_button_red)
            .setMessage("Numero de serie:$manualDeviceId requiere una licencia\n\nContacte al proveedor para obtener una licencia.")
            .setPositiveButton("Salir") { _, _ ->
                StateProvider.pin_drive_status = false
               // subscriptionViewModel.openPaymentFlow(this, manualDeviceId)
                subscriptionViewModel.resetState()
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
            .setTitle("Licencia Vencida")
            .setIcon(R.drawable.background_button_red)
            .setMessage("El numero de serie:$manualDeviceId\n\nrequiere renovar su licencia.$expiredMessage\n\nContacte al proveedor para obtener una licencia.")
            .setPositiveButton("Salir") { _, _ ->
                StateProvider.pin_drive_status = false
               // subscriptionViewModel.openPaymentFlow(this, manualDeviceId)
                subscriptionViewModel.resetState()
            }

            .setCancelable(false)
            .show()
    }

    /**
     * Displays an error dialog when subscription verification fails.
     * This typically occurs due to network issues or server errors.
     * Offers retry functionality to attempt verification again.
     *
     * @param message Error description from the ViewModel (e.g., "No internet connection")
     */
    private fun showErrorDialog(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Verifique su conexion a internet")
            .setMessage("No se pudo verificar la licencia para:\n$manualDeviceId\n\n")
            .setPositiveButton("Salir") { _, _ ->
                StateProvider.pin_drive_status = false
                //subscriptionViewModel.verifyAccess(manualDeviceId)
                subscriptionViewModel.resetState()
            }
            .setCancelable(false)
            .show()

    }

    private fun showErrorDialog2() {
        android.app.AlertDialog.Builder(this)
            .setTitle("CJ9 no tiene acceso a internet")
            .setMessage("Por favor revise su conexion a internet antes de continuar")
            .setPositiveButton("Salir") { _, _ ->
                StateProvider.pin_drive_status = false
                finish()
                //subscriptionViewModel.verifyAccess(manualDeviceId)
               // subscriptionViewModel.resetState()
            }
            .setCancelable(false)
            .show()
    }
}