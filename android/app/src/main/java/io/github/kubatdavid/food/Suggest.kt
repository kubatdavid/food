package io.github.kubatdavid.food

import java.text.Normalizer

/**
 * IDE-style ingredient autocomplete, same rules as the website:
 * match quality (start of name > start of a word > anywhere > fuzzy) first,
 * then how often you've added the item, then how common it is.
 * Accepting a suggestion fills the unit, place and usual package size.
 */
data class Suggestion(
    val label: String,
    val via: String,
    val unit: String,
    val category: String,
    val qty: Double?,
    val count: Int,
)

object Suggest {
    private val separators = Regex("[\\s\\-–,]+")
    private val marks = Regex("\\p{Mn}+")

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")

    class Entry(
        val label: String,
        val aka: List<String>,
        val alt: String,
        var unit: String,
        var category: String,
        val common: Int,
        var qty: Double?,
        var count: Int = 0,
        var have: Boolean = false,
    )

    fun entries(catalog: List<CatalogEntry>, usage: List<NameUsage>, items: List<PantryItem>): List<Entry> {
        val list = catalog.map {
            Entry(it.cs, it.aka, it.en, it.unit, it.category, it.common, it.size.takeIf { s -> s > 0 })
        }.toMutableList()
        val byKey = HashMap<String, Entry>()
        list.forEach { e -> (listOf(e.label, e.alt) + e.aka).forEach { byKey[norm(it)] = e } }
        for (u in usage) {
            val k = norm(u.name)
            val e = byKey.getOrPut(k) {
                Entry(u.name, emptyList(), "", u.unit ?: "g", u.category ?: "Other", 0, null).also { list.add(it) }
            }
            e.count += u.count
            u.unit?.let { e.unit = it }
            u.category?.let { e.category = it }
            u.qty?.takeIf { it > 0 }?.let { e.qty = it }   // your usual package beats the shop default
        }
        for (it in items) {
            val e = byKey.getOrPut(norm(it.name)) {
                Entry(it.name, emptyList(), "", it.unit, it.category, 0, null).also { n -> list.add(n) }
            }
            e.have = true
        }
        return list
    }

    private fun fuzzy(n: String, q: String): Boolean {
        var i = 0
        for (ch in n) {
            if (ch == q[i]) {
                i++
                if (i == q.length) return true
            }
        }
        return false
    }

    private fun rank(name: String, q: String): Double {
        val n = norm(name)
        if (n.isEmpty()) return 0.0
        if (n.startsWith(q)) return 3.0
        if (n.split(separators).any { it.startsWith(q) }) return 2.0
        if (q.length >= 3 && n.contains(q)) return 1.0
        if (q.length >= 3 && n[0] == q[0] && fuzzy(n.replace(separators, ""), q.replace(separators, ""))) return 0.6
        return 0.0
    }

    fun suggest(text: String, entries: List<Entry>, limit: Int = 6): List<Suggestion> {
        val q = norm(text.trim())
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Double, Suggestion>>()
        for (e in entries) {
            var best = rank(e.label, q)
            var via = ""
            for (a in e.aka) {
                val r = rank(a, q) - 0.4
                if (r > best) { best = r; via = a }
            }
            if (e.alt.isNotEmpty()) {
                val r = rank(e.alt, q) - 1.5
                if (r > best) { best = r; via = e.alt }
            }
            if (best <= 0) continue
            val score = best * 1000 + minOf(e.count, 50) * 12 + e.common * 6 + (if (e.have) 4 else 0) - e.label.length * 0.01
            scored += score to Suggestion(e.label, via, e.unit, e.category, e.qty, e.count)
        }
        return scored.sortedByDescending { it.first }.take(limit).map { it.second }
    }
}
