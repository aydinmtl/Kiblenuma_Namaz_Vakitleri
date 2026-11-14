package com.aydinmtl.namazvakitleri

/** Serializes selection changes and accepting their asynchronous results (including A → B → A). */
class SelectionGuard {
    private var generation = 0L

    @Synchronized
    fun <T> capture(read: () -> T): Pair<Long, T> = generation to read()

    @Synchronized
    fun change(write: () -> Unit) {
        generation++
        write()
    }

    @Synchronized
    fun apply(expected: Long, write: () -> Unit): Boolean {
        if (expected != generation) return false
        write()
        return true
    }
}
