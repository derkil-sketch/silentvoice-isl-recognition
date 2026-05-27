package com.example.silentvoice

import android.content.Context
import org.json.JSONObject
import kotlin.math.pow

class MLPredictor(context: Context) {

    private var mean = DoubleArray(5)
    private var std = DoubleArray(5)
    private var classes = arrayOf<String>()

    init {
        loadModel(context)
    }

    private fun loadModel(context: Context) {

        val json = context.assets.open("model_params.json")
            .bufferedReader()
            .use { it.readText() }

        val obj = JSONObject(json)

        val meanArr = obj.getJSONArray("mean").toDoubleArray()
        val stdArr = obj.getJSONArray("std").toDoubleArray()
        val classArr = obj.getJSONArray("classes").toStringArray()

        mean = meanArr
        std = stdArr
        classes = classArr
    }

    fun predict(values: FloatArray): String {
        val normDouble = standardize(values, mean, std)

        // Simple distance classification
        // (placeholder until full RF export)

        var bestClass = classes[0]
        var minDist = Double.MAX_VALUE

        for (cls in classes) {
            val dist = normDouble.sumOf { it.pow(2) }

            if (dist < minDist) {
                minDist = dist
                bestClass = cls
            }
        }

        return bestClass
    }
}

