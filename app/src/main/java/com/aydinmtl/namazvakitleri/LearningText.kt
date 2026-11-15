package com.aydinmtl.namazvakitleri

import android.content.Context

/** Yalnızca sunum çevrilir; kayıtlı soru kimlikleri, adımlar ve Arapça sesler korunur. */
class LearningText(private val context: Context) {
    private fun array(id: Int) = context.resources.getStringArray(id)
    private val levels by lazy { array(R.array.learn_levels) }
    private val hints by lazy { array(R.array.learn_level_hints) }
    private val stages by lazy { array(R.array.learn_tree_stages) }
    private val chapters by lazy { array(R.array.learn_chapters) }
    private val titles by lazy { array(R.array.learn_step_titles) }
    private val questions by lazy { array(R.array.learn_step_questions) }
    private val correct by lazy { array(R.array.learn_step_correct) }
    private val wrongOne by lazy { array(R.array.learn_step_wrong_one) }
    private val wrongTwo by lazy { array(R.array.learn_step_wrong_two) }
    private val info by lazy { array(R.array.learn_step_info) }
    private val recitationTitles by lazy { array(R.array.learn_recitation_titles) }
    private val meanings by lazy { array(R.array.learn_recitation_meanings) }

    fun level(level: QuizLevel): String = levels[level.ordinal]
    fun hint(level: QuizLevel): String = hints[level.ordinal]
    fun tree(stage: Int): String = stages.getOrElse(stage) { "?" }
    fun chapter(chapter: PrayerChapter): String = chapters[chapter.ordinal]
    fun result(stars: Int): String = array(R.array.learn_result_titles)[stars]

    /** Asıl adımın hareketlerini, bölümünü ve vücut kısmını korur; yalnızca sunumu çevirir. */
    fun step(index: Int): PrayerStep {
        val base = PrayerGame.STEPS[index]
        return base.copy(
            title = titles[index], question = questions[index],
            options = listOf(correct[index], wrongOne[index], wrongTwo[index]), info = info[index],
            source = source(base.source),
            moves = PrayerGame.presentMoves(
                base.moves, recitation(Recitations.NIYET),
                context.getString(R.string.learn_greet_right), context.getString(R.string.learn_greet_left),
            ),
        )
    }

    /** Arapça okunuşlar korunur; niyet günlük dilde olduğu için seçili dile çevrilir. */
    fun recitation(original: Recitation): Recitation {
        val index = Recitations.ALL.indexOf(original)
        if (index < 0) return original
        return original.copy(
            title = recitationTitles[index], meaning = meanings[index], source = source(original.source),
            text = if (original == Recitations.NIYET) context.getString(R.string.learn_intention) else original.text,
        )
    }

    fun isIntention(reading: Recitation): Boolean =
        reading == Recitations.NIYET || reading == recitation(Recitations.NIYET)

    fun source(text: String): String = when (Lang.code(context)) {
        "en" -> text.replace(" suresi, ", ", ").replace(". ayet", " (verse)")
            .replace(" suresi (19. sure)", " (surah 19)").replace(" suresi 29", ", verse 29")
        "fr" -> text.replace(" suresi, ", ", ").replace(". ayet", " (verset)")
            .replace(" suresi (19. sure)", " (sourate 19)").replace(" suresi 29", ", verset 29")
        "de" -> text.replace(" suresi, ", ", ").replace(". ayet", " (Vers)")
            .replace(" suresi (19. sure)", " (Sure 19)").replace(" suresi 29", ", Vers 29")
        else -> text
    }

    fun question(text: String, options: List<String>): String = buildString {
        append(QuizSpeech.clean(text))
        options.forEachIndexed { i, option ->
            append('\n').append(context.getString(R.string.learn_option, i + 1, QuizSpeech.clean(option)))
        }
    }

    fun report(flags: Map<String, String>, bank: List<Question>): String = buildString {
        append(context.getString(R.string.learn_report_title)).append('\n')
        for ((id, note) in flags.toSortedMap()) {
            append("\n[").append(id).append("] ")
            val question = bank.firstOrNull { it.id == id }
            if (question == null) append(context.getString(R.string.learn_report_missing))
            else append(question.text).append('\n').append(context.getString(R.string.learn_report_answer, question.correct))
            if (note.isNotBlank()) append('\n').append(context.getString(R.string.learn_report_note, note.trim()))
            append('\n')
        }
    }
}
