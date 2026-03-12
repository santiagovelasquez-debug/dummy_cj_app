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
import kotlinx.coroutines.delay
import kotlin.math.truncate
import kotlin.text.toByte
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
        if(status != _pinDriveStatus.value )
        _pinDriveStatus.value = status
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
    private lateinit var deviceCheckRunnable: Runnable
    private val CHECK_INTERVAL = 2000L // 2000 milisegundos = 2 segundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        footerDias = findViewById(R.id.footer_dias)

        update_days_left(0)
        StateProvider.pin_drive_status = false;


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
            versionTextView.text = "v$version" // Muestra "v1.0.0" por ejemplo
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            // Opcional: manejar el error si no se encuentra la info del paquete
        }

        // dentro de onCreate()
        lifecycleScope.launch {
            AppState.pinDriveStatus.collectLatest { status ->
                if (status == 2) {
                    // acción cuando es true (ej: habilitar botón, navegar, etc.)
                    show_cjclip_not_expired()
                    Log.d("UI", "show_cjclip_not_expired()")
                    findViewById<Button>(R.id.ButtonContinueGreen).setOnClickListener {
                        StateProvider.pin_drive_status = true
                        finish()
                    }
                } else if(status == 0){
                    update_days_left(365)
                    show_cjclip_new()
                    Log.d("UI", "show_cjclip_new()")
                    readCJDongle()
                    Log.d("UI", "readCJDongle()")

                    findViewById<Button>(R.id.ButtonInitBlue).setOnClickListener {
                        /*Sent usb command and validate*/
                        //StateProvider.pin_drive_status = true
                        // Change cj dongle status and set time and date

                        AppState.setPinDriveStatus(2)

                    }
                }
                else if(status == 3){
                    update_days_left(0)
                    show_cjclip_expired()
                    Log.d("UI", "show_cjclip_expired()")
                }
                else if(status == 4){
                    show_cjclip_not_detected()
                    update_days_left(0)
                    Log.d("UI", "show_cjclip_not_detected()")
                }
            }



        }
    }


    private fun update_days_left(days: Int) {
        footerDias.text = "$days DÍAS"

        // Change color when time is expired
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

        // Oculta los demás
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
        // Inicializamos el Runnable aquí
        deviceCheckRunnable = object : Runnable {
            override fun run() {
                Log.d("DeviceCheck", "Intentando detectar el dispositivo USB...")
                val device = UsbHidDevice.factory(this@MainActivity, 0x483, 0x5750)
                if (device == null) {

                    // Si el dispositivo sigue sin detectarse, mostramos el estado correspondiente
                    AppState.setPinDriveStatus(4)
                    // Y volvemos a programar esta misma tarea para dentro de 2 segundos
                    handler.postDelayed(this, CHECK_INTERVAL)

                } else {
                    // Si el dispositivo se encontró, procedemos a abrirlo
                    // El `handler.removeCallbacks(this)` se hace dentro de onUsbHidDeviceConnected
                    openDevice(device)
                }
            }
        }

        // --- Inicia el primer intento de detección ---
        handler.post(deviceCheckRunnable)
    }
    private fun readCJDongle() {
        // Inicializamos el Runnable aquí
        deviceCheckRunnable = object : Runnable {
            override fun run() {
                Log.d("DeviceCheck2", "Intentando detectar el dispositivo USB...")
                val device = UsbHidDevice.factory(this@MainActivity, 0x483, 0x5750)
                if (device == null) {

                    // Si el dispositivo sigue sin detectarse, mostramos el estado correspondiente
                    AppState.setPinDriveStatus(4)
                    // Y volvemos a programar esta misma tarea para dentro de 2 segundos
                    handler.postDelayed(this, CHECK_INTERVAL)

                } else {
                    // Si el dispositivo se encontró, procedemos a abrirlo
                    // El `handler.removeCallbacks(this)` se hace dentro de onUsbHidDeviceConnected
                    openDevice2(device)
                }
            }
        }

        // --- Inicia el primer intento de detección ---
        handler.post(deviceCheckRunnable)
    }

    private fun openDevice(device: UsbHidDevice) {
        device.open(this, object : OnUsbHidDeviceListener {
            override fun onUsbHidDeviceConnected(device: UsbHidDevice) {
                // ¡Once the device is detected remove callbacks!
                handler.removeCallbacks(deviceCheckRunnable)
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
                    val write2 = processDongleResult(result)


                }
                else
                {
                    Log.d("DeviceCheck", "Dispositivo Reading FAIL.")
                }
            }

            override fun onUsbHidDeviceConnectFailed(device: UsbHidDevice) {
                // Si la conexión falla, también detenemos los reintentos para no causar problemas.
                handler.removeCallbacks(deviceCheckRunnable)
                Log.d("DeviceCheck", "Falló la conexión. Reintentos detenidos.")

                StateProvider.pin_drive_status = false;
                AppState.setPinDriveStatus(4)
            }
        })
    }

    private fun openDevice2(device: UsbHidDevice) {
        device.open(this, object : OnUsbHidDeviceListener {
            override fun onUsbHidDeviceConnected(device: UsbHidDevice) {
                // ¡Once the device is detected remove callbacks!
                handler.removeCallbacks(deviceCheckRunnable)
                Log.d("DeviceCheck2", "Dispositivo conectado. Reintentos detenidos.")

                val sendBuffer = ByteArray(64)
                sendBuffer[0] = 3;
                // Change cj dongle status
                sendBuffer[1] = 0x02.toByte()
                device.write(sendBuffer)
                Log.d("DeviceCheck2", "Dispositivo Writing.cj dongle status ")


            }

            override fun onUsbHidDeviceConnectFailed(device: UsbHidDevice) {
                // Si la conexión falla, también detenemos los reintentos para no causar problemas.
                handler.removeCallbacks(deviceCheckRunnable)
                Log.d("DeviceCheck", "Falló la conexión. Reintentos detenidos.")

                StateProvider.pin_drive_status = false;
                AppState.setPinDriveStatus(4)
            }
        })
    }
    /**
    * Process the custom hid configuraion from cjcjip
    * */
    private fun processDongleResult(result: ByteArray?):Boolean {
        var status:Boolean = false
        // Check if the response ID matches the request ID (with offset).
        if (result != null && result.isNotEmpty() && result[0] == (1 + 0x80).toByte()) {            // Safely convert unsigned bytes to Int
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
            val hoursLeft =  (result[14].toInt() and 0xFF shl 8) or (result[15].toInt() and 0xFF)
            val dongleStatus = result[16].toInt() and 0xFF

            // Use a when expression for cleaner status handling.
            status = handleDongleStatus(dongleStatus, hoursLeft)

            // Further processing for RTC can be done here.
            val rtcHr = result[17]
            val rtcMin = result[18]
            // ... and so on.


        } else {
            Log.w("DeviceResult", "Data received but not valid or null.")
            StateProvider.pin_drive_status = false
            AppState.setPinDriveStatus(4)
            status =false
        }
        return status
    }

    private fun handleDongleStatus(dongleStatus: Int, hoursLeft: Int) : Boolean{
        var status:Boolean = false
        // Use a when expression for cleaner status handling.
        when (dongleStatus) {
            2 -> { // Not expired
                //StateProvider.pin_drive_status = true
                update_days_left(hoursLeft / 24)

                AppState.setPinDriveStatus(2)
                Log.d("handleDongleStatus", "Not expired.")

            }
            3 -> { // Expired
                StateProvider.pin_drive_status = false
                AppState.setPinDriveStatus(3)
                Log.d("handleDongleStatus", "Expired.")
            }
            0 -> { // New

                AppState.setPinDriveStatus(0)
                Log.d("handleDongleStatus", "New detected.")
            }
            else -> { // Not detected or other status
                StateProvider.pin_drive_status = false
                Log.d("handleDongleStatus", "Not detected or other status.")
                AppState.setPinDriveStatus(4)

            }
        }
        return status
    }

    fun setTimeAndDate(sendBuffer: ByteArray) {
        // Get current date/time
        val calendar = Calendar.getInstance()

        // Extract calendar values and convert them to BCD format
        val year = rtcByteToBcd2((calendar.get(Calendar.YEAR) - 2000).toByte())
        val month = rtcByteToBcd2((calendar.get(Calendar.MONTH) + 1).toByte()) // In Calendar, January is 0
        val day = rtcByteToBcd2(calendar.get(Calendar.DAY_OF_MONTH).toByte())
        val hour = rtcByteToBcd2(calendar.get(Calendar.HOUR_OF_DAY).toByte()) // 24-hour format
        val minute = rtcByteToBcd2(calendar.get(Calendar.MINUTE).toByte())
        val second = rtcByteToBcd2(calendar.get(Calendar.SECOND).toByte())
        val dayOfWeek = rtcWeekday(calendar.get(Calendar.DAY_OF_WEEK))

        // Populate the sendBuffer with the new time and date values
        sendBuffer[1] = dayOfWeek
        sendBuffer[2] = year
        sendBuffer[3] = month
        sendBuffer[4] = day
        sendBuffer[5] = hour
        sendBuffer[6] = minute
        sendBuffer[7] = second
    }
    private fun rtcByteToBcd2(number: Byte): Byte {
        // Convert to Int for calculations to avoid byte overflow issues
        var num = number.toInt()
        var bcdHigh = 0

        while (num >= 10) {
            bcdHigh++
            num -= 10
        }

        // Perform bitwise operations and return the result as a Byte
        return ((bcdHigh shl 4) or num).toByte()
    }
    private fun rtcWeekday(dayOfWeek: Int): Byte {
        val rtcDay = when (dayOfWeek) {
            1 -> 7 // Sunday
            2 -> 1 // Monday
            3 -> 2 // Tuesday
            4 -> 3 // Wednesday
            5 -> 4 // Thursday
            6 -> 5 // Friday
            7 -> 6 // Saturday
            else -> (dayOfWeek%7)+1
        }
        // The final expression seems to be a safeguard.
        // In Java, `7 % 7` is `0`, which doesn't match the logic for Sunday.
        // However, to keep the logic identical to the original, we keep the modulo.
        // If Sunday should be 7, the modulo might be a bug.
        // If the expected output for Sunday is 0, then the logic is correct.
        return rtcDay.toByte()
    }


    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending posts to avoid memory leaks.
        handler.removeCallbacks(deviceCheckRunnable)
        Log.d("DeviceCheck", "Actividad destruida. Todos los reintentos detenidos.")
    }



}






