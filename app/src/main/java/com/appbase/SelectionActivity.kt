package com.appbase

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.ComponentActivity
import android.app.AlertDialog
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope

class SelectionActivity : ComponentActivity() {
    
    private lateinit var btnUsbConnection: Button
    private lateinit var btnSubscription: Button
    
    // TODO: Replace with actual API endpoints when backend is ready
    private val API_BASE_URL = "https://your-api.com/api"
    private val CHECK_SUBSCRIPTION_ENDPOINT = "$API_BASE_URL/subscription/check"
    private val REGISTER_SUBSCRIPTION_ENDPOINT = "$API_BASE_URL/subscription/register"
    
    // Set to true to use mock data for testing
    private val USE_MOCK_DATA = true
    
    //private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection)
        
        initializeViews()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        btnUsbConnection = findViewById(R.id.btnUsbConnection)
        btnSubscription = findViewById(R.id.btnSubscription)
    }
    
    private fun setupClickListeners() {
        btnUsbConnection.setOnClickListener {
            // Navigate to MainActivity for USB connection
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        
        btnSubscription.setOnClickListener {
            // Validate subscription
            validateSubscription()
        }
    }
    
    private fun validateSubscription() {
        val uniqueId = getUniqueDeviceId()
        
        // Show loading dialog
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Validating subscription...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                if (USE_MOCK_DATA) {
                    // Mock validation for testing
                    delay(1500) // Simulate network delay
                    progressDialog.dismiss()
                    handleMockValidation(uniqueId)
                } else {
                    // Real API calls
                    val subscriptionStatus = checkSubscriptionStatus(uniqueId)
                    
                    if (subscriptionStatus) {
                        progressDialog.dismiss()
                        showSuccessDialog()
                    } else {
                        val registrationSuccess = registerSubscription(uniqueId)
                        progressDialog.dismiss()
                        
                        if (registrationSuccess) {
                            showRegistrationSuccessDialog()
                        } else {
                            showErrorDialog("Failed to register subscription")
                        }
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                showErrorDialog("Network error: ${e.message}")
            }
        }
    }
    
    private fun handleMockValidation(deviceId: String) {
        // Mock logic for testing - randomly decide if subscription is valid
       // val isValid = (0..1).random() == 1
        val isValid = true
        
        AlertDialog.Builder(this)
            .setTitle("Mock Validation")
            .setMessage("Device ID: ${deviceId.take(8)}...\n\n" +
                       "This is mock validation for testing.\n" +
                       "Result: ${if (isValid) "Valid Subscription" else "No Subscription Found"}\n\n" +
                       "Set USE_MOCK_DATA = false when backend is ready.")
            .setPositiveButton("OK") { _, _ ->
                if (isValid) {
                    // For testing, go to MainActivity
//                    val intent = Intent(this, MainActivity::class.java)
//                    startActivity(intent)
                    launchExternalApp("com.appdependiente") // TODO: replace with target package name
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun launchExternalApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            AlertDialog.Builder(this)
                .setTitle("App Not Found")
                .setMessage("The required application is not installed on this device.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun getUniqueDeviceId(): String {
        // Get Android ID as unique identifier
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    private suspend fun checkSubscriptionStatus(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // TODO: Update with actual endpoint and parameters
            val url = URL("$CHECK_SUBSCRIPTION_ENDPOINT?deviceId=$deviceId")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            // TODO: Add any required headers (API key, auth token, etc.)
            // connection.setRequestProperty("Authorization", "Bearer YOUR_TOKEN")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                
                connection.disconnect()
                
                // TODO: Update based on actual API response structure
                return@withContext jsonResponse.optBoolean("isValid", false)
            }
            
            connection.disconnect()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private suspend fun registerSubscription(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // TODO: Update with actual endpoint
            val url = URL(REGISTER_SUBSCRIPTION_ENDPOINT)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            // TODO: Add any required headers
            // connection.setRequestProperty("Authorization", "Bearer YOUR_TOKEN")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // TODO: Update payload based on backend requirements
            val jsonPayload = JSONObject().apply {
                put("deviceId", deviceId)
                put("timestamp", System.currentTimeMillis())
                // Add any other required fields
                // put("appVersion", BuildConfig.VERSION_NAME)
                // put("platform", "android")
            }
            
            // Send POST data
            connection.outputStream.use { os ->
                val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Subscription Valid")
            .setMessage("Your subscription is active. You can proceed.")
            .setPositiveButton("Continue") { _, _ ->
                // Navigate to main functionality
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showRegistrationSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Registration Successful")
            .setMessage("Your device has been registered. Please contact support to activate your subscription.")
            .setPositiveButton("OK") { _, _ ->
                // Stay on selection screen
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ ->
                validateSubscription()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        //coroutineScope.cancel()
    }
}
