package io.curblink.demo

/** Monotonic wall time, explicit 30x speed. Pausing discards the paused interval. */
class DemoClock {
    private var last: Long? = null
    private var fraction = 0L
    fun step(nowMs: Long, running: Boolean): Int {
        val before = last
        last = nowMs
        if (!running || before == null || nowMs < before) { fraction = 0; return 0 }
        val scaled = (nowMs - before) * 30 + fraction
        fraction = scaled % 1000
        return (scaled / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
