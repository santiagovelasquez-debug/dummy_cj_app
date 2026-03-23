package com.appbase

import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.appbase.ui.subscription.SubscriptionUiState
import com.appbase.ui.subscription.SubscriptionViewModel
import com.appbase.util.DeviceIdProvider
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Hub activity that allows users to choose between two connection modes:
 * 1. USB Connection - Navigate to MainActivity for hardware dongle verification
 * 2. Subscription - Verify cloud-based subscription status via API
 * 
 * This activity acts as a router after the splash screen, directing users
 * to the appropriate verification flow based on their choice.
 */
class SelectionActivity : ComponentActivity() {

    private lateinit var btnUsbConnection: Button
    private lateinit var btnSubscription: Button

    /**
     * ViewModel injected by Koin dependency injection framework.
     * Handles subscription verification logic and maintains UI state.
     * The `by viewModel()` delegate lazily retrieves the ViewModel from Koin.
     */
    private val subscriptionViewModel: SubscriptionViewModel by viewModel()

    /**
     * Unique device identifier used to verify subscription status.
     * Lazily initialized using DeviceIdProvider which:
     * - Primarily uses ANDROID_ID (stable per app installation)
     * - Falls back to a generated UUID if ANDROID_ID is unavailable
     * 
     * This ID is sent to the server to check if the device has an active subscription.
     */
    private val deviceId: String by lazy { DeviceIdProvider.getDeviceId(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection)

        initializeViews()
        setupClickListeners()
        observeSubscriptionState()
    }

    /**
     * Binds UI elements to their corresponding view references.
     * Called once during onCreate to cache button references for later use.
     */
    private fun initializeViews() {
        btnUsbConnection = findViewById(R.id.btnUsbConnection)
        btnSubscription  = findViewById(R.id.btnSubscription)
    }

    /**
     * Configures click handlers for both navigation options.
     * 
     * USB Connection button: Directly navigates to MainActivity for
     * hardware-based dongle verification.
     * 
     * Subscription button: Initiates an API call to verify if the
     * device has an active cloud subscription.
     */
    private fun setupClickListeners() {
        // Navigate to USB dongle verification screen
        btnUsbConnection.setOnClickListener {
            startActivity(android.content.Intent(this, MainActivity::class.java))
        }

        // Trigger subscription verification via REST API
        btnSubscription.setOnClickListener {
            // Triggers GET /api/verify-access/:deviceId
            subscriptionViewModel.verifyAccess(deviceId)
        }
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
                        btnSubscription.isEnabled = false
                        btnSubscription.text = "Validando..."
                    }

                    is SubscriptionUiState.AccessGranted -> {
                        // Re-enable button and restore label
                        btnSubscription.isEnabled = true
                        btnSubscription.text = "Suscripción"
                        // ✅ Active subscription — show details and offer to launch external app
                        showAccessGrantedDialog(state.plan, state.remainingDays, state.message)
                    }

                    is SubscriptionUiState.NoSubscription -> {
                        btnSubscription.isEnabled = true
                        btnSubscription.text = "Suscripción"
                        // ❌ No subscription found — prompt user to subscribe
                        showNoSubscriptionDialog()
                    }

                    is SubscriptionUiState.Expired -> {
                        btnSubscription.isEnabled = true
                        btnSubscription.text = "Suscripción"
                        // ❌ Subscription expired — prompt user to renew
                        showExpiredDialog(state.expiredAt)
                    }

                    is SubscriptionUiState.Error -> {
                        btnSubscription.isEnabled = true
                        btnSubscription.text = "Suscripción"
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
            .setTitle("✅ Acceso Activo")
            .setMessage("$message\n\nPlan: $plan\nDías restantes: $remainingDays")
            .setPositiveButton("Continuar") { _, _ ->
                // Launch the external app that requires subscription
                launchExternalApp("com.appdependiente")
                // Reset ViewModel state to Idle for next interaction
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
            .setTitle("Sin Suscripción")
            .setMessage("No se encontró suscripción para este dispositivo.\n\n¿Deseas suscribirte ahora?")
            .setPositiveButton("Suscribirse") { _, _ ->
                // Open external payment flow (e.g., web browser with payment page)
                subscriptionViewModel.openPaymentFlow(this, deviceId)
                subscriptionViewModel.resetState()
            }
            .setNegativeButton("Cancelar") { _, _ ->
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
        // Build expiration message only if date is available
        val expiredMessage = if (expiredAt != null) "\n\nVenció el: $expiredAt" else ""
        android.app.AlertDialog.Builder(this)
            .setTitle("Suscripción Vencida")
            .setMessage("Tu suscripción ha expirado.$expiredMessage\n\n¿Deseas renovarla ahora?")
            .setPositiveButton("Renovar") { _, _ ->
                // Open payment flow for subscription renewal
                subscriptionViewModel.openPaymentFlow(this, deviceId)
                subscriptionViewModel.resetState()
            }
            .setNegativeButton("Cancelar") { _, _ ->
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
            .setTitle("Error de Conexión")
            .setMessage("No se pudo verificar la suscripción.\n\n$message")
            .setPositiveButton("Reintentar") { _, _ ->
                // Retry the verification with the same device ID
                subscriptionViewModel.verifyAccess(deviceId)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                subscriptionViewModel.resetState()
            }
            .show()
    }

    // ─── External app launcher ────────────────────────────────────────────────

    /**
     * Attempts to launch an external application by its package name.
     * Used to open the dependent app (com.appdependiente) after subscription
     * verification succeeds.
     * 
     * If the target app is not installed, shows an informative error dialog
     * instead of crashing.
     * 
     * @param packageName The package name of the app to launch (e.g., "com.appdependiente")
     */
    private fun launchExternalApp(packageName: String) {
        // Get launch intent for the specified package
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            // App is installed - launch it
            startActivity(intent)
        } else {
            // App not found - show error dialog
            android.app.AlertDialog.Builder(this)
                .setTitle("App No Encontrada")
                .setMessage("La aplicación requerida no está instalada en este dispositivo.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
