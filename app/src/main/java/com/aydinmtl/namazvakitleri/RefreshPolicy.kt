package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object RefreshPolicy {
    fun needsData(id: String?, computed: Boolean, last: LocalDate?, today: LocalDate): Boolean =
        id != null && !computed && (last == null || ChronoUnit.DAYS.between(today, last) < 10)
}
