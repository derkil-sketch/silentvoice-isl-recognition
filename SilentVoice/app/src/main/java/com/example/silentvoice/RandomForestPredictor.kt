package com.example.silentvoice
 
import android.content.Context
import org.json.JSONObject
import android.util.Log
 
/**
 * Singleton RF predictor.
 * - JSON parsed ONCE into primitive arrays (no repeated boxing/unboxing)
 * - Early-exit voting: stops as soon as any class hits majority
 * - Thread-safe: safe to call from Dispatchers.Default
 */
class RandomForestPredictor private constructor(context: Context) {
 
    private val mean:  DoubleArray
    private val std:   DoubleArray
    val classes:       Array<String>
    private val nClasses:     Int
    private val nEstimators:  Int
 
    private val childrenLeft:  Array<IntArray>
    private val childrenRight: Array<IntArray>
    private val feature:       Array<IntArray>
    private val threshold:     Array<DoubleArray>
    private val value:         Array<Array<DoubleArray>>  // [tree][node][class_prob]
 
    init {
        val json = context.assets.open("gesture_rf_model.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(json)
 
        val meanArr = root.getJSONArray("mean")
        val stdArr  = root.getJSONArray("std")
        mean = DoubleArray(meanArr.length()) { meanArr.getDouble(it) }
        std  = DoubleArray(stdArr.length())  { stdArr.getDouble(it) }
 
        val classArr = root.getJSONArray("classes")
        nClasses     = classArr.length()
        classes      = Array(nClasses) { classArr.getString(it) }

        // Temporary debug (remove once exporter format is confirmed)
        Log.d(
            "RF",
            "Mean[0]=${mean.getOrNull(0)} Std[2]=${std.getOrNull(2)} Classes=${classes.toList()}"
        )

        // Robust — infer it from how many trees are actually in the JSON

        val treesArr  = root.getJSONArray("trees")
        nEstimators  = treesArr.length()

        childrenLeft  = Array(nEstimators) { IntArray(0) }
        childrenRight = Array(nEstimators) { IntArray(0) }
        feature       = Array(nEstimators) { IntArray(0) }
        threshold     = Array(nEstimators) { DoubleArray(0) }
        value         = Array(nEstimators) { Array(0) { DoubleArray(0) } }

        for (t in 0 until nEstimators) {
            val tree   = treesArr.getJSONObject(t)
            val cl     = tree.getJSONArray("children_left")
            val cr     = tree.getJSONArray("children_right")
            val f      = tree.getJSONArray("feature")
            val th     = tree.getJSONArray("threshold")
            val v      = tree.getJSONArray("value")
            val n      = cl.length()
 
            childrenLeft[t]  = IntArray(n)    { cl.getInt(it) }
            childrenRight[t] = IntArray(n)    { cr.getInt(it) }
            feature[t]       = IntArray(n)    { f.getInt(it) }
            threshold[t]     = DoubleArray(n) { th.getDouble(it) }
            value[t]         = Array(n) { ni ->
                val probs = v.getJSONArray(ni).getJSONArray(0)
                DoubleArray(nClasses) { c -> probs.getDouble(c) }
            }
        }
        Log.d("RF", "Loaded $nEstimators trees, $nClasses classes")
    }
 
    fun predict(raw: FloatArray): String {
        // Scale input
        val scaled = DoubleArray(raw.size) { i -> (raw[i] - mean[i]) / std[i] }
 
        // Vote with early exit
        val votes    = IntArray(nClasses)
        val majority = nEstimators / 2 + 1
 
        for (t in 0 until nEstimators) {
            var node = 0
            while (childrenLeft[t][node] != -1) {
                node = if (scaled[feature[t][node]] <= threshold[t][node])
                    childrenLeft[t][node] else childrenRight[t][node]
            }
            // Pick highest-prob class at this leaf
            val probs = value[t][node]
            var best  = 0
            for (c in 1 until nClasses) if (probs[c] > probs[best]) best = c
            votes[best]++
 
            // Early exit
            if (votes[best] >= majority) return classes[best]
        }
 
        var winner = 0
        for (c in 1 until nClasses) if (votes[c] > votes[winner]) winner = c
        return classes[winner]
    }
 
    companion object {
        @Volatile private var instance: RandomForestPredictor? = null
 
        fun getInstance(context: Context): RandomForestPredictor =
            instance ?: synchronized(this) {
                instance ?: RandomForestPredictor(context.applicationContext).also { instance = it }
            }
    }
}

