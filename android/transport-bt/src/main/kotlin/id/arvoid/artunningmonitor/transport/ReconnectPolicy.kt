package id.arvoid.artunningmonitor.transport

/**
 * Kebijakan jeda auto-reconnect (sederhana, tanpa state waktu — pemanggil
 * yang menjadwalkan coroutine/Handler berdasarkan hasil [nextDelayMs]).
 */
class ReconnectPolicy(
    private val delaysMs: LongArray = longArrayOf(2_000, 5_000, 10_000, 30_000),
) {
    var attempts: Int = 0
        private set

    /** Jeda berikutnya; null = habis (biarkan pengguna menyambung manual). */
    fun nextDelayMs(): Long? {
        if (attempts >= delaysMs.size) return null
        val d = delaysMs[attempts]
        attempts++
        return d
    }

    fun reset() {
        attempts = 0
    }
}
