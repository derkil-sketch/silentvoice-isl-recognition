package com.example.silentvoice
 
/**
 * WordBuffer — fixes letter-by-letter output.
 *
 * Rules:
 *  - A prediction is "confirmed" only after STABLE_COUNT identical results in a row.
 *  - Multi-char labels (YES, NO, THANKYOU, STOP …) → emitted immediately as a word.
 *  - Single-char labels (A, B, C …) → buffered, emitted as a word on change or pause.
 *  - After PAUSE_MS silence, current buffer is flushed.
 *
 * Usage:
 *   val buf = WordBuffer { word -> showOnScreen(word) }
 *   buf.push("T")       // streak 1 — nothing yet
 *   buf.push("T")       // streak 2
 *   buf.push("T")       // streak 3 — confirmed, buffered as char 'T'
 *   buf.push("THANKYOU")// confirmed after 3× — emitted whole as "THANKYOU"
 */
class WordBuffer(private val onWord: (String) -> Unit) {
 
    companion object {
        private const val STABLE_COUNT = 3      // repeats needed to confirm a token
        private const val PAUSE_MS     = 1500L  // ms gap → flush pending chars
    }
 
    private var lastRaw       = ""
    private var streak        = 0
    private var lastConfirmed = ""
    private val charBuf       = StringBuilder()
    private var lastPushMs    = 0L
 
    fun push(prediction: String) {
        val now = System.currentTimeMillis()
 
        // Flush on silence gap
        if (lastPushMs > 0 && now - lastPushMs > PAUSE_MS) flush()
        lastPushMs = now
 
        if (prediction == lastRaw) streak++ else { lastRaw = prediction; streak = 1 }
 
        // Confirmed when stable AND not a repeat of last confirmed
        if (streak >= STABLE_COUNT && prediction != lastConfirmed) {
            lastConfirmed = prediction
            emit(prediction)
        }
    }
 
    /** Call on disconnect or reset to output any buffered chars. */
    fun flush() {
        if (charBuf.isNotEmpty()) {
            onWord(charBuf.toString())
            charBuf.clear()
        }
        lastConfirmed = ""
    }
 
    private fun emit(token: String) {
        if (token.length == 1 && token[0].isLetter()) {
            charBuf.append(token)           // accumulate single letters
        } else {
            if (charBuf.isNotEmpty()) flush() // flush pending letters first
            onWord(token)                    // emit whole word immediately
        }
    }
}

