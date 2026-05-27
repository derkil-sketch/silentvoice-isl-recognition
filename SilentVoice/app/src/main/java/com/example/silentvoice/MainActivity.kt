package com.example.silentvoice
 
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import java.io.IOException
import java.util.Locale
import android.os.Build
 
class MainActivity : AppCompatActivity() {
 
    private lateinit var tvPrediction: TextView
    private lateinit var tvStatus:     TextView
    private lateinit var btnConnect:   MaterialButton
    private lateinit var btnSpeak:     MaterialButton
    private lateinit var btnDetails:   MaterialButton
 
    private val ACTION_USB_PERMISSION = "com.example.silentvoice.USB_PERMISSION"
    private lateinit var usbManager: UsbManager
    private var usbPort: UsbSerialPort? = null
    private var readJob: Job? = null
 
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                setStatus("Permission granted — connecting...")
                openPort(device)
            } else {
                setStatus("USB permission denied — tap Connect again")
            }
        }
    }
 
    private lateinit var wordBuffer: WordBuffer

    private var lastSpoken = ""
    private lateinit var tts: TextToSpeech
 
    companion object {
        val sensorLog     = ArrayDeque<String>(200)
        var lastRawValues = FloatArray(5)
    }
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
 
        tvPrediction = findViewById(R.id.tvPrediction)
        tvStatus     = findViewById(R.id.tvStatus)
        btnConnect   = findViewById(R.id.btnConnect)
        btnSpeak     = findViewById(R.id.btnSpeak)
        btnDetails   = findViewById(R.id.btnDetails)
 
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
registerReceiver(usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
 
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts.language = Locale.US }
        wordBuffer = WordBuffer { word -> runOnUiThread { showWord(word) } }

        // Shared prediction pipeline
        DataBridge.init(applicationContext)

 
        btnConnect.isEnabled = false
        setStatus("Loading model...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val p = RandomForestPredictor.getInstance(this@MainActivity)
                withContext(Dispatchers.Main) {
                    setStatus("Ready  |  ${p.classes.size} gestures loaded")
                    btnConnect.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setStatus("Model error: ${e.message}") }
            }
        }

        // Receive confirmed gestures from DataBridge
        DataBridge.gestureLiveData.observe(this) { gesture ->
            if (gesture.isNotEmpty()) {
                wordBuffer.push(gesture)
            }
        }

 
        btnConnect.setOnClickListener { if (usbPort == null) connectUsb() else disconnectUsb() }
        btnDetails.setOnClickListener { startActivity(Intent(this, DetailsActivity::class.java)) }
        btnSpeak.setOnClickListener   { if (lastSpoken.isNotEmpty()) speak(lastSpoken) }
    }
 
    private fun connectUsb() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            setStatus("No USB device found — check cable & OTG adapter")
            return
        }
        val device = drivers[0].device
        setStatus("Found: ${device.productName ?: "USB device"} — requesting permission...")
        if (usbManager.hasPermission(device)) {
            openPort(device)
        } else {
            val pi = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, pi)
        }
    }
 
    private fun openPort(device: UsbDevice) {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) { setStatus("Driver not found"); return }
        val connection = usbManager.openDevice(device)
        if (connection == null) { setStatus("Failed to open — unplug and replug cable"); return }
        try {
            val port = drivers[0].ports[0]
            port.open(connection)
            port.setParameters(115200, UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbPort = port
            btnConnect.text = "Disconnect"
            setStatus("Connected to ESP32 ✓")
            startReading(port)
        } catch (e: Exception) {
            setStatus("Port open failed: ${e.message}")
        }
    }
 
    private fun startReading(port: UsbSerialPort) {
        readJob = lifecycleScope.launch(Dispatchers.IO) {
            val buf = ByteArray(1024)
            val sb  = StringBuilder()
            try {
                while (isActive) {
                    val len = port.read(buf, 200)
                    if (len > 0) {
                        sb.append(String(buf, 0, len))
                        var nl: Int
                        while (sb.indexOf("\n").also { nl = it } >= 0) {
                            val line = sb.substring(0, nl).trim()
                            sb.delete(0, nl + 1)
                            if (line.isNotEmpty()) processLine(line)
                        }
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    setStatus("Read error: ${e.message}")
                    btnConnect.text = "Connect"
                    usbPort = null
                }
            }
        }
    }
 
    private fun processLine(line: String) {
        val parts = line.split(",")
        if (parts.size != 5) return
        val values = try { FloatArray(5) { parts[it].trim().toFloat() } } catch (_: NumberFormatException) { return }

        lastRawValues = values
        if (sensorLog.size >= 200) sensorLog.removeFirst()
        sensorLog.addLast(line)

        // Buffer + consistency + prediction happen inside DataBridge.
        DataBridge.onSensorReading(values)
    }

 
    private fun showWord(word: String) {
        lastSpoken = word
        tvPrediction.animate().alpha(0f).setDuration(80).withEndAction {
            tvPrediction.text = word
            tvPrediction.animate().alpha(1f).setDuration(150).start()
        }.start()
        speak(word)
    }
 
    private fun speak(text: String) =
        tts.speak(text.lowercase(), TextToSpeech.QUEUE_FLUSH, null, null)
 
    private fun disconnectUsb() {
        readJob?.cancel()
        wordBuffer.flush()
        runCatching { usbPort?.close() }
        usbPort = null
        btnConnect.text = "Connect"
        setStatus("Disconnected")
    }
 
    private fun setStatus(msg: String) { tvStatus.text = msg }
 
    override fun onDestroy() {
        disconnectUsb()
        unregisterReceiver(usbPermissionReceiver)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}
