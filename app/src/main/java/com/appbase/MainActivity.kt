package com.appbase


import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppState {
    private val _pinDriveStatus = MutableStateFlow(5)

    val pinDriveStatus: StateFlow<Int> = _pinDriveStatus.asStateFlow()

    fun setPinDriveStatus(status: Int) {
        if (status != _pinDriveStatus.value) {
            _pinDriveStatus.value = status
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var textViewStatus: TextView
    private lateinit var buttonInitBlue: Button
    private lateinit var buttonContinueGreen: Button
    private lateinit var imageViewNotdetected: ImageView
    private lateinit var imageViewExpired: ImageView
    private lateinit var footerDias: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var deviceCheckRunnable: Runnable? = null
    private var isPollingUsb = false
    private val CHECK_INTERVAL = 2000L // 2000 milisegundos = 2 segundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        footerDias = findViewById(R.id.footer_dias)

        update_days_left(0)
        StateProvider.pin_drive_status = false

        // Inicializa las vistas usando findViewById
        textViewStatus = findViewById(R.id.text_view_status)
        buttonInitBlue = findViewById(R.id.ButtonInitBlue)
        buttonContinueGreen = findViewById(R.id.ButtonContinueGreen)
        imageViewNotdetected = findViewById(R.id.imageViewNotdetected)
        imageViewExpired = findViewById(R.id.imageViewExpired)

        readCJDongle1()

        // --- CÓDIGO PARA MOSTRAR LA VERSIÓN (AGREGADO) ---
        try {
            val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            val versionTextView = findViewById<TextView>(R.id.version_text)
            versionTextView.text = "v$version"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        lifecycleScope.launch {
            AppState.pinDriveStatus.collectLatest { status ->
                if (isFinishing || isDestroyed) return@collectLatest

                when (status) {
                    2 -> {
                        show_cjclip_not_expired()
                        Log.d("UI", "show_cjclip_not_expired()")
                        buttonContinueGreen.setOnClickListener {
                            StateProvider.pin_drive_status = true
                            finish()
                        }
                    }

                    0 -> {
                        update_days_left(365)
                        show_cjclip_new()
                        Log.d("UI", "show_cjclip_new()")
                        readCJDongle()
                        Log.d("UI", "readCJDongle()")

                        buttonInitBlue.setOnClickListener {
                            AppState.setPinDriveStatus(2)
                        }
                    }

                    3 -> {
                        update_days_left(0)
                        show_cjclip_expired()
                        Log.d("UI", "show_cjclip_expired()")
                    }

                    4 -> {
                        show_cjclip_not_detected()
                        update_days_left(0)
                        Log.d("UI", "show_cjclip_not_detected()")
                    }
                }
            }
        }
    }

    private fun update_days_left(days: Int) {
        footerDias.text = "$days DÍAS"

        if (days == 0) {
            footerDias.setTextColor(resources.getColor(R.color.red, null))
        } else {
            footerDias.setTextColor(resources.getColor(R.color.green, null))
        }
    }

    /**
     * Show info when cj clip is new and it's not counting
     */
    private fun show_cjclip_new() {
        textViewStatus.visibility = View.VISIBLE
        buttonInitBlue.visibility = View.VISIBLE

        buttonContinueGreen.visibility = View.GONE
        imageViewNotdetected.visibility = View.GONE
        imageViewExpired.visibility = View.GONE
    }

    /**
     * Show info when cj clip is not detected
     */
    private fun show_cjclip_not_detected() {
        textViewStatus.visibility = View.GONE
        buttonInitBlue.visibility = View.GONE
        buttonContinueGreen.visibility = View.GONE
        imageViewNotdetected.visibility = View.VISIBLE
        imageViewExpired.visibility = View.GONE
    }

    /**
     * Show info when cj clip is counting and it's not expired
     */
    private fun show_cjclip_expired() {
        textViewStatus.visibility = View.GONE
        buttonInitBlue.visibility = View.GONE
        buttonContinueGreen.visibility = View.GONE
        imageViewNotdetected.visibility = View.GONE
        imageViewExpired.visibility = View.VISIBLE
    }

    /**
     * Show info when cj clip is expired
     */
    private fun show_cjclip_not_expired() {
        textViewStatus.visibility = View.GONE
        buttonInitBlue.visibility = View.GONE
        buttonContinueGreen.visibility = View.VISIBLE
        imageViewNotdetected.visibility = View.GONE
        imageViewExpired.visibility = View.GONE
    }

    /**
     * Check if a cjclip is connected and if it's not check avery two seconds
     * a valid dongle is connected
     */
    private fun readCJDongle1() {
        if (isPollingUsb) return
        isPollingUsb = true

        deviceCheckRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) {
                    isPollingUsb = false
                    return
                }

                Log.d("DeviceCheck", "Intentando detectar el dispositivo USB...")
                val device = UsbHidDevice.factory(this@MainActivity, 0x483, 0x5750)
                if (device == null) {
                    AppState.setPinDriveStatus(4)
                    handler.postDelayed(this, CHECK_INTERVAL)
                } else {
                    isPollingUsb = false
                    openDevice(device)
                }
            }
        }

        handler.post(deviceCheckRunnable!!)
    }

    private fun readCJDongle() {
        if (isPollingUsb) return
        isPollingUsb = true

        deviceCheckRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) {
                    isPollingUsb = false
                    return
                }

                Log.d("DeviceCheck2", "Intentando detectar el dispositivo USB...")
                val device = UsbHidDevice.factory(this@MainActivity, 0x483, 0x5750)
                if (device == null) {
                    AppState.setPinDriveStatus(4)
                    handler.postDelayed(this, CHECK_INTERVAL)
                } else {
                    isPollingUsb = false
                    openDevice2(device)
                }
            }
        }

        handler.post(deviceCheckRunnable!!)
    }

    private fun openDevice(device: UsbHidDevice) {
        device.open(this, object : OnUsbHidDeviceListener {
            override fun onUsbHidDeviceConnected(device: UsbHidDevice) {
                deviceCheckRunnable?.let { handler.removeCallbacks(it) }
                Log.d("DeviceCheck", "Dispositivo conectado. Reintentos detenidos.")

                val sendBuffer = ByteArray(64)
                sendBuffer[0] = 1
                setTimeAndDate(sendBuffer)
                Log.d("DeviceCheck", "Dispositivo Writing.")
                device.write(sendBuffer)

                Log.d("DeviceCheck", "Dispositivo Reading.")
                val result = device.read(64)

                if (result != null) {
                    Log.d("DeviceCheck", "Dispositivo Reading OK.")
                    processDongleResult(result)
                } else {
                    Log.d("DeviceCheck", "Dispositivo Reading FAIL.")
                }
            }

            override fun onUsbHidDeviceConnectFailed(device: UsbHidDevice) {
                deviceCheckRunnable?.let { handler.removeCallbacks(it) }
                Log.d("DeviceCheck", "Falló la conexión. Reintentos detenidos.")

                StateProvider.pin_drive_status = false
                AppState.setPinDriveStatus(4)
            }
        })
    }

    private fun openDevice2(device: UsbHidDevice) {
        device.open(this, object : OnUsbHidDeviceListener {
            override fun onUsbHidDeviceConnected(device: UsbHidDevice) {
                deviceCheckRunnable?.let { handler.removeCallbacks(it) }
                Log.d("DeviceCheck2", "Dispositivo conectado. Reintentos detenidos.")

                val sendBuffer = ByteArray(64)
                sendBuffer[0] = 3
                sendBuffer[1] = 0x02.toByte()
                device.write(sendBuffer)
                Log.d("DeviceCheck2", "Dispositivo Writing.cj dongle status ")
            }

            override fun onUsbHidDeviceConnectFailed(device: UsbHidDevice) {
                deviceCheckRunnable?.let { handler.removeCallbacks(it) }
                Log.d("DeviceCheck", "Falló la conexión. Reintentos detenidos.")

                StateProvider.pin_drive_status = false
                AppState.setPinDriveStatus(4)
            }
        })
    }

    /**
     * Process the custom hid configuraion from cjcjip
     */
    private fun processDongleResult(result: ByteArray?): Boolean {
        var status = false

        if (result != null && result.isNotEmpty() && result[0] == (1 + 0x80).toByte()) {
            val ivdata = (result[1].toInt() and 0xFF shl 24) or
                    (result[2].toInt() and 0xFF shl 16) or
                    (result[3].toInt() and 0xFF shl 8) or
                    (result[4].toInt() and 0xFF)

            val serialNumber = (result[5].toInt() and 0xFF shl 24) or
                    (result[6].toInt() and 0xFF shl 16) or
                    (result[7].toInt() and 0xFF shl 8) or
                    (result[8].toInt() and 0xFF)

            val partNumber = (result[9].toInt() and 0xFF shl 24) or
                    (result[10].toInt() and 0xFF shl 16) or
                    (result[11].toInt() and 0xFF shl 8) or
                    (result[12].toInt() and 0xFF)

            val alarmStatus = result[13].toInt() and 0xFF
            val hoursLeft = (result[14].toInt() and 0xFF shl 8) or (result[15].toInt() and 0xFF)
            val dongleStatus = result[16].toInt() and 0xFF

            status = handleDongleStatus(dongleStatus, hoursLeft)

            val rtcHr = result[17]
            val rtcMin = result[18]
        } else {
            Log.w("DeviceResult", "Data received but not valid or null.")
            StateProvider.pin_drive_status = false
            AppState.setPinDriveStatus(4)
            status = false
        }
        return status
    }

    private fun handleDongleStatus(dongleStatus: Int, hoursLeft: Int): Boolean {
        var status = false
        when (dongleStatus) {
            2 -> {
                update_days_left(hoursLeft / 24)
                AppState.setPinDriveStatus(2)
                Log.d("handleDongleStatus", "Not expired.")
            }

            3 -> {
                StateProvider.pin_drive_status = false
                AppState.setPinDriveStatus(3)
                Log.d("handleDongleStatus", "Expired.")
            }

            0 -> {
                AppState.setPinDriveStatus(0)
                Log.d("handleDongleStatus", "New detected.")
            }

            else -> {
                StateProvider.pin_drive_status = false
                Log.d("handleDongleStatus", "Not detected or other status.")
                AppState.setPinDriveStatus(4)
            }
        }
        return status
    }

    fun setTimeAndDate(sendBuffer: ByteArray) {
        val calendar = Calendar.getInstance()

        val year = rtcByteToBcd2((calendar.get(Calendar.YEAR) - 2000).toByte())
        val month = rtcByteToBcd2((calendar.get(Calendar.MONTH) + 1).toByte())
        val day = rtcByteToBcd2(calendar.get(Calendar.DAY_OF_MONTH).toByte())
        val hour = rtcByteToBcd2(calendar.get(Calendar.HOUR_OF_DAY).toByte())
        val minute = rtcByteToBcd2(calendar.get(Calendar.MINUTE).toByte())
        val second = rtcByteToBcd2(calendar.get(Calendar.SECOND).toByte())
        val dayOfWeek = rtcWeekday(calendar.get(Calendar.DAY_OF_WEEK))

        sendBuffer[1] = dayOfWeek
        sendBuffer[2] = year
        sendBuffer[3] = month
        sendBuffer[4] = day
        sendBuffer[5] = hour
        sendBuffer[6] = minute
        sendBuffer[7] = second
    }

    private fun rtcByteToBcd2(number: Byte): Byte {
        var num = number.toInt()
        var bcdHigh = 0

        while (num >= 10) {
            bcdHigh++
            num -= 10
        }

        return ((bcdHigh shl 4) or num).toByte()
    }

    private fun rtcWeekday(dayOfWeek: Int): Byte {
        val rtcDay = when (dayOfWeek) {
            1 -> 7
            2 -> 1
            3 -> 2
            4 -> 3
            5 -> 4
            6 -> 5
            7 -> 6
            else -> (dayOfWeek % 7) + 1
        }
        return rtcDay.toByte()
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceCheckRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        isPollingUsb = false
        Log.d("DeviceCheck", "Actividad destruida. Todos los reintentos detenidos.")
    }
}