package com.example.silentvoice

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.example.silentvoice.RandomForestPredictor.Companion.getInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * DataBridge — shared, reactive sensor->prediction pipeline.
 *
 * Rules implemented:
 *  - Keep last 10 readings
 *  - If stddev of any single sensor across those 10 exceeds 30.0, discard buffer
 *  - When 10 consistent readings collected: average each sensor and predict gesture
 *  - After prediction: clear buffer
 */
object DataBridge {
    private const val SENSOR_COUNT = 5
    private const val BUFFER_SIZE = 10
    private const val STDDEV_THRESHOLD = 30.0f

    val gestureLiveData: MutableLiveData<String> = MutableLiveData()

    /** For UI/debug (e.g., DetailsActivity chart). Updated when an averaged vector is accepted. */
    val averagedSensorsLiveData: MutableLiveData<FloatArray> = MutableLiveData()

    @Volatile
    private var predictor: RandomForestPredictor? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val rollingBuffer = ArrayDeque<FloatArray>(BUFFER_SIZE)

    fun init(context: Context) {
        if (predictor != null) return
        predictor = getInstance(context.applicationContext)
    }

    /** Called by the USB reader for each incoming, parsed FloatArray(5). */
    fun onSensorReading(values: FloatArray) {
        if (values.size != SENSOR_COUNT) return

        // Keep buffer management lightweight/synchronous; heavy compute runs on Dispatchers.Default.
        synchronized(this) {
            if (rollingBuffer.size == BUFFER_SIZE) {
                rollingBuffer.removeFirst()
            }
            rollingBuffer.addLast(values)

            if (rollingBuffer.size < BUFFER_SIZE) return

            val bufferSnapshot = Array(BUFFER_SIZE) { idx -> rollingBuffer.elementAt(idx) }

            // Consistency check (stddev per sensor)
            if (!isConsistent(bufferSnapshot)) {
                rollingBuffer.clear()
                return
            }

            // Average across 10 consistent readings
            val averaged = averageSensors(bufferSnapshot)

            // After accepting: clear buffer immediately (rule 6/7)
            rollingBuffer.clear()

            // Predict on Dispatchers.Default
            val rf = predictor ?: return
            scope.launch {
                val gesture = rf.predict(averaged)
                averagedSensorsLiveData.postValue(averaged)
                gestureLiveData.postValue(gesture)
            }
        }
    }

    private fun isConsistent(buffer: Array<FloatArray>): Boolean {
        // For each sensor, compute stddev across 10 readings.
        for (sensorIdx in 0 until SENSOR_COUNT) {
            var sum = 0.0f
            for (reading in buffer) sum += reading[sensorIdx]
            val mean = sum / BUFFER_SIZE

            var variance = 0.0f
            for (reading in buffer) {
                val d = reading[sensorIdx] - mean
                variance += d * d
            }

            val stddev = sqrt(variance / BUFFER_SIZE)
            if (stddev > STDDEV_THRESHOLD) return false
        }
        return true
    }

    private fun averageSensors(buffer: Array<FloatArray>): FloatArray {
        val out = FloatArray(SENSOR_COUNT)
        for (sensorIdx in 0 until SENSOR_COUNT) {
            var sum = 0.0f
            for (reading in buffer) sum += reading[sensorIdx]
            out[sensorIdx] = sum / BUFFER_SIZE
        }
        return out
    }
}

