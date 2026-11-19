package io.github.kubatdavid.food

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Czech display names for the stored English keys, plus small formatting helpers. */
object Cs {
    val categories = listOf("Fridge", "Freezer", "Pantry", "Fruit & veg", "Spices & herbs", "Other")
    private val categoryNames = mapOf(
        "Fridge" to "Lednice",
        "Freezer" to "Mrazák",
        "Pantry" to "Spíž",
        "Fruit & veg" to "Ovoce a zelenina",
        "Spices & herbs" to "Koření a bylinky",
        "Other" to "Ostatní",
    )
    val units = listOf("g", "kg", "ml", "l", "pcs", "pack", "tbsp", "tsp")
    private val unitNames = mapOf(
        "g" to "g", "kg" to "kg", "ml" to "ml", "l" to "l",
        "pcs" to "ks", "pack" to "bal.", "tbsp" to "lžíce", "tsp" to "lžička",
    )
    private val steps = mapOf("g" to 50.0, "ml" to 50.0, "kg" to 0.5, "l" to 0.5)

    fun category(key: String) = categoryNames[key] ?: key
    fun unit(key: String) = unitNames[key] ?: key
    fun step(unit: String) = steps[unit] ?: 1.0
    fun categoryOrder(key: String) = categories.indexOf(key).let { if (it < 0) categories.size else it }

    fun qty(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString().replace('.', ',')
    }

    fun amount(v: Double, unit: String) = "${qty(v)} ${unit(unit)}"

    /** Days from today until an ISO date (negative = past), or null. */
    fun daysUntil(iso: String?): Long? = try {
        if (iso.isNullOrBlank()) null else ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(iso))
    } catch (e: Exception) {
        null
    }

    private val shortDate = DateTimeFormatter.ofPattern("d. M. yyyy", Locale.forLanguageTag("cs"))
    fun date(iso: String?): String = try {
        if (iso.isNullOrBlank()) "" else LocalDate.parse(iso).format(shortDate)
    } catch (e: Exception) {
        iso ?: ""
    }

    /** Parses "1,5" or "1.5"; null when it isn't a non-negative number. */
    fun parseQty(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
}

fun PantryItem.isOut() = qty <= 0.0
fun PantryItem.isSoon(): Boolean {
    val d = Cs.daysUntil(expires) ?: return false
    return d <= 3 && !isOut()
}
