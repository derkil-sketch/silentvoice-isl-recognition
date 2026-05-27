package com.example.silentvoice

import org.json.JSONArray

fun standardize(input: FloatArray, mean: DoubleArray, std: DoubleArray): DoubleArray {
    val output = DoubleArray(input.size)

    for (i in input.indices) {
        output[i] = (input[i] - mean[i]) / std[i]
    }

    return output
}

// JSON helper extensions
fun JSONArray.toDoubleArray(): DoubleArray {
    return DoubleArray(length()) { getDouble(it) }
}

fun JSONArray.toFloatArray(): FloatArray {
    return FloatArray(length()) { getDouble(it).toFloat() }
}

fun JSONArray.toIntArray(): IntArray {
    return IntArray(length()) { getInt(it) }
}

fun JSONArray.toStringArray(): Array<String> {
    return Array(length()) { getString(it) }
}

