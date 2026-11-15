package com.aydinmtl.namazvakitleri

/** Monotonic timestamps, so adjusting the wall clock cannot make an old fix look fresh. */
object LocationFreshness {
    fun accepts(nowNanos: Long, fixNanos: Long, maxAgeMs: Long): Boolean =
        fixNanos > 0 && fixNanos <= nowNanos && maxAgeMs > 0 &&
            (nowNanos - fixNanos) / 1_000_000L < maxAgeMs
}
