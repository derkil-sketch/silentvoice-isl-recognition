package com.example.silentvoice
 
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
 
class DetailsActivity : AppCompatActivity() {
 
    private lateinit var tvThumb:   TextView
    private lateinit var tvIndex:   TextView
    private lateinit var tvMiddle:  TextView
    private lateinit var tvRing:    TextView
    private lateinit var tvLittle:  TextView
    private lateinit var tvLog:     TextView
    private lateinit var lineChart: LineChart
 
    private val sets  = mutableListOf<LineDataSet>()
    private var chartX = 0f
 
    private val handler   = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 100) }
    }
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
 
        tvThumb  = findViewById(R.id.tvThumb)
        tvIndex  = findViewById(R.id.tvIndex)
        tvMiddle = findViewById(R.id.tvMiddle)
        tvRing   = findViewById(R.id.tvRing)
        tvLittle = findViewById(R.id.tvLittle)
        tvLog    = findViewById(R.id.tvLog)
        lineChart= findViewById(R.id.lineChart)
 
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        setupChart()
    }
 
    override fun onResume()  { super.onResume();  handler.post(refresher) }
    override fun onPause()   { super.onPause();   handler.removeCallbacks(refresher) }
 
    private fun refresh() {
        val v = MainActivity.lastRawValues
        tvThumb .text = "Thumb\n${v[0].toInt()}"
        tvIndex .text = "Index\n${v[1].toInt()}"
        tvMiddle.text = "Middle\n${v[2].toInt()}"
        tvRing  .text = "Ring\n${v[3].toInt()}"
        tvLittle.text = "Little\n${v[4].toInt()}"
        tvLog.text    = MainActivity.sensorLog.takeLast(20).joinToString("\n")
        addChartEntry(v)
    }
 
    private fun setupChart() {
        val colors = listOf("#FF6B6B","#4ECDC4","#45B7D1","#96CEB4","#FFEAA7")
        val labels = listOf("Thumb","Index","Middle","Ring","Little")
        val dataSets = colors.zip(labels).map { (c, l) ->
            LineDataSet(null, l).apply {
                color = Color.parseColor(c); lineWidth = 1.5f
                setDrawCircles(false); setDrawValues(false)
            }.also { sets.add(it) }
        }
        lineChart.data = LineData(dataSets as List<ILineDataSet>)
        lineChart.description.isEnabled = false
        lineChart.setBackgroundColor(Color.TRANSPARENT)
        lineChart.xAxis.textColor = Color.WHITE
        lineChart.axisLeft.textColor = Color.WHITE
        lineChart.axisRight.isEnabled = false
        lineChart.legend.textColor = Color.WHITE
    }
 
    private fun addChartEntry(v: FloatArray) {
        val data = lineChart.data ?: return
        v.forEachIndexed { i, value -> data.addEntry(Entry(chartX, value), i) }
        chartX += 1f
        data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(60f)
        lineChart.moveViewToX(chartX)
    }
}

