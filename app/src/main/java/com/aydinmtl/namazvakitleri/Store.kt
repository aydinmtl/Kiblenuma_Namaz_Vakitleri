package com.aydinmtl.namazvakitleri

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Seçilen yer, ayarlar ve 30 günlük vakit önbelleği (SharedPreferences). */
object Store {
    private const val PREFS = "namaz"
    private const val MIN_RETRY_MS = 15 * 60 * 1000L        // başarısız denemeden sonra 15 dk bekle
    private const val REFRESH_WHEN_DAYS_LEFT = 10            // 10 günden az veri kalınca yenile

    private var cachedJson: String? = null
    private var cachedDays: List<PrayerDay> = emptyList()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun districtId(ctx: Context): String? = prefs(ctx).getString("districtId", null)
    fun placeName(ctx: Context): String = prefs(ctx).getString("placeName", "") ?: ""

    fun notificationsOn(ctx: Context): Boolean = prefs(ctx).getBoolean("notify", true)
    fun setNotificationsOn(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("notify", on).apply()
    }

    /** Vakit bazında bildirim. Varsayılan: Güneş hariç hepsi açık. */
    fun notifyFor(ctx: Context, index: Int): Boolean = prefs(ctx).getBoolean("notify_$index", index != SUNRISE)
    fun setNotifyFor(ctx: Context, index: Int, on: Boolean) {
        prefs(ctx).edit().putBoolean("notify_$index", on).apply()
    }

    // ---- Otomatik konum ----

    fun autoLocation(ctx: Context): Boolean = prefs(ctx).getBoolean("autoLocation", false)
    fun setAutoLocation(ctx: Context, on: Boolean) {
        // Açarken de kapatırken de eski "son otomatik nokta" silinir. Böylece otomatik konum
        // her açıldığında konum mutlaka yeniden kontrol edilir (eski kayıt yüzünden atlanmaz).
        prefs(ctx).edit()
            .putBoolean("autoLocation", on)
            .remove("autoLat").remove("autoLon").remove("autoDistrict")
            .apply()
    }

    /** Konum izni açıklaması bir kez gösterilir. */
    fun locationIntroShown(ctx: Context): Boolean = prefs(ctx).getBoolean("locIntro", false)
    fun setLocationIntroShown(ctx: Context) {
        prefs(ctx).edit().putBoolean("locIntro", true).apply()
    }

    /**
     * Vakitlerin en son otomatik olarak belirlendiği nokta (enlem, boylam).
     * Bu nokta yalnızca o sırada bulunan yer HÂLÂ seçili yerse geçerlidir. Yer sonradan
     * elle değiştirildiyse (ör. Cenevre'deyken İstanbul seçildiyse) null döner; böylece
     * otomatik konum "yer değişmedi" deyip yanlış şehirde kalmaz.
     */
    fun autoPoint(ctx: Context): Pair<Double, Double>? {
        val p = prefs(ctx)
        if (p.getString("autoDistrict", null) != districtId(ctx)) return null
        val lat = p.getString("autoLat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("autoLon", null)?.toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    /** Konumu kaydeder; o anki seçili yerle birlikte (bkz. [autoPoint]). */
    fun saveAutoPoint(ctx: Context, lat: Double, lon: Double) {
        prefs(ctx).edit()
            .putString("autoLat", lat.toString()).putString("autoLon", lon.toString())
            .putString("autoDistrict", districtId(ctx))
            .apply()
    }

    fun lastDay(ctx: Context): LocalDate? = days(ctx).lastOrNull()?.date

    // ---- Hesaplama yöntemi ----

    fun method(ctx: Context): CalcMethod = CalcMethod.byId(prefs(ctx).getString("method", null))
    fun setMethod(ctx: Context, m: CalcMethod) {
        prefs(ctx).edit().putString("method", m.id).apply()
    }

    /** İkindi: false = standart (Şafii/Diyanet), true = Hanefi (asr-ı sani). */
    fun asrHanafi(ctx: Context): Boolean = prefs(ctx).getBoolean("asrHanafi", false)
    fun setAsrHanafi(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("asrHanafi", on).apply()
    }

    /** Seçili yerin koordinatı: hesaplama yöntemleri, kıble ve camiler için. */
    fun placePoint(ctx: Context): Pair<Double, Double>? {
        val p = prefs(ctx)
        val lat = p.getString("placeLat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("placeLon", null)?.toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    fun savePlacePoint(ctx: Context, point: Pair<Double, Double>?) {
        val e = prefs(ctx).edit()
        if (point == null) e.remove("placeLat").remove("placeLon")
        else e.putString("placeLat", point.first.toString()).putString("placeLon", point.second.toString())
        e.apply()
    }

    /** Diyanet yerlerinin (adından bulunmuş) koordinat önbelleği: en yakın yer hesabı için. */
    fun geoCache(ctx: Context, placeId: String): Pair<Double, Double>? {
        val s = prefs(ctx).getString("geo_$placeId", null) ?: return null
        val parts = s.split(",")
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    fun setGeoCache(ctx: Context, placeId: String, p: Pair<Double, Double>) {
        prefs(ctx).edit().putString("geo_$placeId", "${p.first},${p.second}").apply()
    }

    // ---- Hatırlatma ve ezan ----

    /** Vakitten kaç dakika önce hatırlatılsın (0 = kapalı). */
    fun reminderMinutes(ctx: Context): Int = prefs(ctx).getInt("reminderMin", 0)
    fun setReminderMinutes(ctx: Context, min: Int) {
        prefs(ctx).edit().putInt("reminderMin", min).apply()
    }

    fun adhanOn(ctx: Context): Boolean = prefs(ctx).getBoolean("adhanOn", false) && adhanUri(ctx) != null
    fun setAdhanOn(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("adhanOn", on).apply()
    }

    fun adhanUri(ctx: Context): String? = prefs(ctx).getString("adhanUri", null)
    fun adhanName(ctx: Context): String = prefs(ctx).getString("adhanName", "") ?: ""
    fun setAdhan(ctx: Context, uri: String?, name: String) {
        prefs(ctx).edit().putString("adhanUri", uri).putString("adhanName", name).apply()
    }

    /** Telefon sessiz/titreşimdeyken ezan çalınmasın. */
    fun adhanRespectSilent(ctx: Context): Boolean = prefs(ctx).getBoolean("adhanSilent", true)
    fun setAdhanRespectSilent(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("adhanSilent", on).apply()
    }

    // ---- Bilgi yarışması (Aile Halkası) ----

    fun quizSession(ctx: Context): QuizSession? = QuizSession.fromJson(prefs(ctx).getString("quizSession", null))
    fun saveQuizSession(ctx: Context, s: QuizSession?) {
        val e = prefs(ctx).edit()
        if (s == null) e.remove("quizSession") else e.putString("quizSession", s.toJson())
        e.apply()
    }

    /**
     * Ağacın puanı. Önceki sürümde her doğru cevap 1 puandı; o puanlar korunur.
     * Artık yalnızca ilk kez doğru bilinen soru puan getirir (bkz. [markSolved]).
     */
    fun treePoints(ctx: Context): Int = prefs(ctx).getInt("treePoints", 0)

    /** Daha önce en az bir kez doğru bilinen soruların kimlikleri. */
    fun solvedIds(ctx: Context): Set<String> = prefs(ctx).getStringSet("quizSolved", null)?.toSet() ?: emptySet()

    /**
     * Soru doğru bilindi. İlk kez bilindiyse ağaca 1 puan eklenir ve true döner;
     * daha önce bilinmişse hiçbir şey değişmez, false döner.
     */
    fun markSolved(ctx: Context, id: String): Boolean {
        val solved = solvedIds(ctx)
        if (id in solved) return false
        // Not: getStringSet'in verdiği küme değiştirilmemeli; her zaman yeni küme yazılır.
        prefs(ctx).edit()
            .putStringSet("quizSolved", solved + id)
            .putInt("treePoints", treePoints(ctx) + 1)
            .apply()
        return true
    }

    /** Yanlış bilinen ve henüz doğru bilinmemiş sorular ("Yanlışlarımı tekrar et" için). */
    fun wrongIds(ctx: Context): Set<String> = prefs(ctx).getStringSet("quizWrong", null)?.toSet() ?: emptySet()
    fun setWrong(ctx: Context, id: String, wrong: Boolean) {
        val now = wrongIds(ctx)
        prefs(ctx).edit().putStringSet("quizWrong", if (wrong) now + id else now - id).apply()
    }

    /** Bir seviyenin destesinde bu tura kadar sorulmuş sorular (tekrar gelmesin diye). */
    fun seenIds(ctx: Context, level: QuizLevel): Set<String> =
        prefs(ctx).getStringSet("quizSeen_${level.id}", null)?.toSet() ?: emptySet()
    fun saveSeenIds(ctx: Context, level: QuizLevel, ids: Set<String>) {
        prefs(ctx).edit().putStringSet("quizSeen_${level.id}", ids).apply()
    }

    /** Son yarışmada sırayla oynayan aile üyeleri (bir dahaki sefere hazır gelsin). */
    fun quizPlayers(ctx: Context): List<String> =
        QuizDeck.parsePlayers(prefs(ctx).getString("quizPlayers", "") ?: "")
    fun saveQuizPlayers(ctx: Context, players: List<String>) {
        prefs(ctx).edit().putString("quizPlayers", players.joinToString(", ")).apply()
    }

    /**
     * Ağacı sıfırla: ağaç tohuma döner, bütün sorular yeniden ağacı büyütebilir.
     * Hedefler, yanlışlar listesi ve oyuncular silinmez.
     */
    fun resetTree(ctx: Context) {
        prefs(ctx).edit().putInt("treePoints", 0).remove("quizSolved").apply()
    }

    /** Günün sorusu en son hangi gün cevaplandı ("2026-11-11"). */
    fun dailyDoneDate(ctx: Context): String? = prefs(ctx).getString("dailyDone", null)
    fun setDailyDone(ctx: Context, date: LocalDate) {
        prefs(ctx).edit().putString("dailyDone", date.toString()).apply()
    }

    /** Akşam vakti girince "günün sorusu" bildirimi gönderilsin mi (varsayılan kapalı). */
    fun dailyNotify(ctx: Context): Boolean = prefs(ctx).getBoolean("dailyNotify", false)
    fun setDailyNotify(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("dailyNotify", on).apply()
    }

    /** "Bu soruda hata var" diye işaretlenen sorular ve notları (bkz. QuizFlags). */
    fun quizFlags(ctx: Context): Map<String, String> = QuizFlags.fromJson(prefs(ctx).getString("quizFlags", null))
    fun setQuizFlag(ctx: Context, id: String, note: String?) {
        val now = quizFlags(ctx)
        val next = if (note == null) now - id else now + (id to note)
        prefs(ctx).edit().putString("quizFlags", QuizFlags.toJson(next)).apply()
    }
    fun clearQuizFlags(ctx: Context) {
        prefs(ctx).edit().remove("quizFlags").apply()
    }

    fun goals(ctx: Context): List<FamilyGoal> = FamilyGoal.listFromJson(prefs(ctx).getString("goals", null))
    fun saveGoals(ctx: Context, list: List<FamilyGoal>) {
        prefs(ctx).edit().putString("goals", FamilyGoal.listToJson(list)).apply()
    }

    // ---- Kaza çetelesi ve zikirmatik ----

    fun kaza(ctx: Context, i: Int): Int = prefs(ctx).getInt("kaza_$i", 0)
    fun setKaza(ctx: Context, i: Int, n: Int) {
        prefs(ctx).edit().putInt("kaza_$i", maxOf(0, n)).apply()
    }

    fun zikirCount(ctx: Context): Int = prefs(ctx).getInt("zikirCount", 0)
    fun zikirTarget(ctx: Context): Int = prefs(ctx).getInt("zikirTarget", 33)
    fun zikirRounds(ctx: Context): Int = prefs(ctx).getInt("zikirRounds", 0)
    fun saveZikir(ctx: Context, count: Int, target: Int, rounds: Int) {
        prefs(ctx).edit().putInt("zikirCount", count).putInt("zikirTarget", target).putInt("zikirRounds", rounds).apply()
    }

    fun askedNotificationPermission(ctx: Context): Boolean = prefs(ctx).getBoolean("askedNotif", false)
    fun setAskedNotificationPermission(ctx: Context) {
        prefs(ctx).edit().putBoolean("askedNotif", true).apply()
    }

    /**
     * "Hesaplanan yer" için kullanılan sahte ilçe numarası. Diyanet'in listesinde bulunulan yere
     * yakın bir ilçe yoksa vakitler konumdan hesaplanır; o zaman districtId bu değeri taşır.
     */
    const val COMPUTED_ID = "hesap"

    /** Vakitler Diyanet listesinden değil, konumdan hesaplanarak mı gösteriliyor? */
    fun isComputed(ctx: Context): Boolean = districtId(ctx) == COMPUTED_ID

    /**
     * Yeri "konumdan hesaplanan yer" olarak kaydeder (Diyanet listesi yok).
     * Vakitler [point] koordinatından hesaplanır (bkz. Times.days).
     */
    fun saveComputedLocation(ctx: Context, placeName: String, point: Pair<Double, Double>) {
        prefs(ctx).edit()
            .putString("districtId", COMPUTED_ID)
            .putString("placeName", placeName)
            .remove("times")
            .putString("placeLat", point.first.toString()).putString("placeLon", point.second.toString())
            .apply()
    }

    /** Hesaplanan yerdeyken Diyanet listesine en erken ne zaman yeniden bakılacağı (ms). */
    fun computedRecheckAt(ctx: Context): Long = prefs(ctx).getLong("computedRecheckAt", 0L)
    fun setComputedRecheckAt(ctx: Context, at: Long) {
        prefs(ctx).edit().putLong("computedRecheckAt", at).apply()
    }

    fun saveLocation(ctx: Context, districtId: String, placeName: String, timesJson: String) {
        prefs(ctx).edit()
            .putString("districtId", districtId)
            .putString("placeName", placeName)
            .putString("times", timesJson)
            .putLong("lastAttempt", System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun days(ctx: Context): List<PrayerDay> {
        val json = prefs(ctx).getString("times", null) ?: return emptyList()
        if (json != cachedJson) {
            cachedDays = Diyanet.parseDays(json)
            cachedJson = json
        }
        return cachedDays
    }

    /** Veri bitmek üzereyse ve kısa süre önce denenmediyse true. */
    fun needsRefresh(ctx: Context): Boolean {
        if (districtId(ctx) == null) return false
        if (isComputed(ctx)) return false // hesaplanan yerde indirilecek liste yok
        // Diyanet dışı bir yöntem seçili olsa da Diyanet verisi güncel tutulur, çünkü:
        //  - yerin koordinatı bulunamazsa uygulama Diyanet vakitlerini gösterir (Times.effectiveMethod);
        //    önceden burada "yöntem Diyanet değil" diye yenileme yapılmıyordu ve ~30 gün sonra
        //    vakitler tamamen bitiyordu,
        //  - Hicri tarih Diyanet takvimine bu veriyle hizalanır.
        // Ayda bir kez küçük bir indirme; pil ve veri etkisi önemsiz.
        val sinceLast = System.currentTimeMillis() - prefs(ctx).getLong("lastAttempt", 0L)
        if (sinceLast < MIN_RETRY_MS) return false
        val last = days(ctx).lastOrNull()?.date ?: return true
        return ChronoUnit.DAYS.between(LocalDate.now(), last) < REFRESH_WHEN_DAYS_LEFT
    }

    /** Vakitleri internetten yeniler. Ağ kullanır: arka planda çağır. */
    fun refresh(ctx: Context): Boolean {
        val id = districtId(ctx) ?: return false
        if (id == COMPUTED_ID) return true // hesaplanan yer: vakitler telefonda hesaplanır, indirme yok
        prefs(ctx).edit().putLong("lastAttempt", System.currentTimeMillis()).apply()
        return try {
            val json = Diyanet.fetchTimes(id)
            if (Diyanet.parseDays(json).isEmpty()) {
                false
            } else {
                prefs(ctx).edit().putString("times", json).apply()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ---------------- Namaz Öğreniyorum oyunu ----------------

    /** Oyunda kaldığı adım (0 = en baş). Oyun bitince 0'a döner. */
    fun prayerStep(ctx: Context): Int = prefs(ctx).getInt("prayerStep", 0)
    fun setPrayerStep(ctx: Context, step: Int) {
        prefs(ctx).edit().putInt("prayerStep", step).apply()
    }

    /** Seçilen figür: true kız, false erkek, null henüz seçilmedi. */
    fun prayerGirl(ctx: Context): Boolean? =
        if (prefs(ctx).contains("prayerGirl")) prefs(ctx).getBoolean("prayerGirl", false) else null
    fun setPrayerGirl(ctx: Context, girl: Boolean) {
        prefs(ctx).edit().putBoolean("prayerGirl", girl).apply()
    }

    /** Namaz oyununda dualar sesli mi okunsun (true) yoksa yalnızca yazı mı ilerlesin (false). */
    fun prayerSound(ctx: Context): Boolean = prefs(ctx).getBoolean("prayerSound", true)
    fun setPrayerSound(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("prayerSound", on).apply()
    }

    /** Namaz oyunu kaç kez baştan sona tamamlandı. */
    fun prayerWins(ctx: Context): Int = prefs(ctx).getInt("prayerWins", 0)
    fun addPrayerWin(ctx: Context) {
        prefs(ctx).edit().putInt("prayerWins", prayerWins(ctx) + 1).apply()
    }
}
