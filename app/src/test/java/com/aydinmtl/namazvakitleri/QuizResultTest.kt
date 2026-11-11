package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tur sonucu yıldızları: doğru oranına göre 0-3 yıldız. */
class QuizResultTest {
    @Test
    fun yildizlar() {
        assertEquals(3, QuizResult.stars(10, 10))
        assertEquals(3, QuizResult.stars(9, 10))
        assertEquals(2, QuizResult.stars(8, 10))
        assertEquals(2, QuizResult.stars(6, 10))
        assertEquals(1, QuizResult.stars(3, 10))
        assertEquals(0, QuizResult.stars(2, 10))
        assertEquals(0, QuizResult.stars(0, 0)) // boş tur çökmesin
    }

    @Test
    fun herYildizIcinSimgeVeBaslikVar() {
        assertEquals(4, QuizResult.EMOJI.size)
        assertEquals(4, QuizResult.TITLE.size)
    }
}
