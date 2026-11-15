package com.aydinmtl.namazvakitleri

/** Seçim, veri yenileme ve sonuç kabulünü aynı kilitle sıralar. */
class SelectionGuard {
    private var generation = 0L
    private var pendingManual: Long? = null

    @Synchronized
    fun <T> capture(read: () -> T): Pair<Long, T> = generation to read()

    @Synchronized
    fun <T> automatic(read: () -> T): Pair<Long, T>? =
        if (pendingManual != null) null else generation to read()

    @Synchronized
    fun beginManual(): Long {
        generation++
        pendingManual = generation
        return generation
    }

    @Synchronized
    fun cancelManual(token: Long) {
        if (pendingManual == token) {
            pendingManual = null
            generation++
        }
    }

    @Synchronized
    fun finishManual(token: Long, write: () -> Unit): Boolean {
        if (pendingManual != token) return false
        try {
            write()
        } finally {
            pendingManual = null
            generation++
        }
        return true
    }

    @Synchronized
    fun change(write: () -> Unit) {
        generation++
        write()
    }

    @Synchronized
    fun isCurrentRequest(expected: Long): Boolean = expected == generation && pendingManual == null

    @Synchronized
    fun apply(expected: Long, write: () -> Unit): Boolean {
        if (!isCurrentRequest(expected)) return false
        generation++
        write()
        return true
    }

    @Synchronized
    fun ifCurrent(expected: Long, publish: () -> Unit): Boolean {
        if (expected != generation) return false
        publish()
        return true
    }
}

/** Hesap kilitsiz yapılır; yalnızca en yeni çağrı güncel veriyi yayımlayabilir. */
internal class GuardedPublication(private val selection: SelectionGuard) {
    private var latest = 0L

    fun <S, T> run(capture: () -> Pair<Long, S>, compute: (S) -> T, publish: (T) -> Unit): Boolean {
        val ticket = synchronized(this) { ++latest }
        val (generation, state) = capture()
        val result = compute(state)
        var accepted = false
        selection.ifCurrent(generation) {
            synchronized(this) {
                if (ticket == latest) {
                    publish(result)
                    accepted = true
                }
            }
        }
        return accepted
    }
}
