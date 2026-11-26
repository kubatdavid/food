package io.github.kubatdavid.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A snackbar message with an undo action. */
class Undoable(val text: String, val undo: () -> Unit)

/** What the add/edit sheet hands back. */
data class Draft(
    val name: String,
    val qty: Double,
    val unit: String,
    val category: String,
    val expires: String? = null,
)

class FoodViewModel : ViewModel() {
    private val api = Api()
    private val writes = Mutex()   // one write at a time, in order

    private val _items = MutableStateFlow<List<PantryItem>>(emptyList())
    private val _shop = MutableStateFlow<List<ShopItem>>(emptyList())
    private val _names = MutableStateFlow<List<NameUsage>>(emptyList())
    private val _catalog = MutableStateFlow<List<CatalogEntry>>(emptyList())
    private val _recipes = MutableStateFlow<List<RecipeRow>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private val _refreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _undoables = MutableSharedFlow<Undoable>(extraBufferCapacity = 8)

    val items: StateFlow<List<PantryItem>> = _items
    val shop: StateFlow<List<ShopItem>> = _shop
    val names: StateFlow<List<NameUsage>> = _names
    val catalog: StateFlow<List<CatalogEntry>> = _catalog
    val recipes: StateFlow<List<RecipeRow>> = _recipes
    val loaded: StateFlow<Boolean> = _loaded
    val refreshing: StateFlow<Boolean> = _refreshing
    val error: StateFlow<String?> = _error
    val messages: SharedFlow<String> = _messages
    val undoables: SharedFlow<Undoable> = _undoables

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                // coroutineScope: a failed request (e.g. offline) is rethrown here and caught below,
                // instead of also travelling up to viewModelScope, where it would crash the app.
                coroutineScope {
                    val items = async { api.items() }
                    val shop = async { api.shopping() }
                    val names = async { runCatching { api.names() }.getOrNull() }
                    val recipes = async { runCatching { api.recipes() }.getOrNull() }
                    val catalog = async { if (_catalog.value.isEmpty()) runCatching { api.catalog() }.getOrNull() else null }
                    _items.value = items.await()
                    _shop.value = shop.await()
                    names.await()?.let { _names.value = it }
                    recipes.await()?.let { _recipes.value = it }
                    catalog.await()?.let { _catalog.value = it }
                }
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Spíž není dostupná."
            } finally {
                _loaded.value = true
                _refreshing.value = false
            }
        }
    }

    private fun write(ok: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                try {
                    block()
                    _error.value = null
                    ok?.let { _messages.emit(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.emit("Nepodařilo se uložit: ${e.message}")
                    runCatching { _items.value = api.items(); _shop.value = api.shopping() }
                }
            }
        }
    }

    private fun Draft.toJson(withExpiry: Boolean): JsonObject = buildJsonObject {
        put("name", name)
        put("qty", qty)
        put("unit", unit)
        put("category", category)
        if (withExpiry) {
            if (expires.isNullOrBlank()) put("expires", JsonNull) else put("expires", expires)
        }
    }

    private suspend fun reloadNames() {
        runCatching { api.names() }.getOrNull()?.let { _names.value = it }
    }

    fun suggestionEntries(): List<Suggest.Entry> = Suggest.entries(_catalog.value, _names.value, _items.value)

    // ---------- pantry ----------
    fun addItem(d: Draft) = write("Přidáno: ${Cs.amount(d.qty, d.unit)} ${d.name}") {
        _items.value = api.addItem(d.toJson(withExpiry = true))
        reloadNames()
    }

    fun updateItem(id: String, d: Draft) = write("Uloženo: ${d.name}") {
        _items.value = api.patchItem(id, d.toJson(withExpiry = true))
    }

    fun setQty(item: PantryItem, qty: Double) {
        val q = maxOf(0.0, Math.round(qty * 100) / 100.0)
        _items.value = _items.value.map { if (it.id == item.id) it.copy(qty = q) else it }   // instant feedback
        write { _items.value = api.patchItem(item.id, buildJsonObject { put("qty", q) }) }
    }

    /** One tap: the item ran out (qty 0). It stays listed so it can go on the shopping list; the snackbar can undo it. */
    fun markOut(item: PantryItem) {
        val before = item.qty
        setQty(item, 0.0)
        _undoables.tryEmit(Undoable("Došlo: ${item.name}") { setQty(item, before) })
    }

    fun deleteItem(item: PantryItem) = write("Odebráno: ${item.name}") {
        _items.value = api.deleteItem(item.id)
    }

    // ---------- shopping list ----------
    fun shopAdd(d: Draft) = write("Na seznam: ${Cs.amount(d.qty, d.unit)} ${d.name}") {
        _shop.value = api.shopAdd(d.toJson(withExpiry = false)).shopping
    }

    fun shopToggle(s: ShopItem, done: Boolean) {
        _shop.value = _shop.value.map { if (it.id == s.id) it.copy(done = done) else it }
        write { _shop.value = api.shopPatch(s.id, buildJsonObject { put("done", done) }).shopping }
    }

    fun shopDelete(s: ShopItem) = write { _shop.value = api.shopDelete(s.id).shopping }

    fun shopClearDone() = write("Odškrtnuté smazány.") { _shop.value = api.shopClearDone().shopping }

    fun shopToPantry() {
        viewModelScope.launch {
            writes.withLock {
                try {
                    val r = api.shopToPantry()
                    _shop.value = r.shopping
                    r.items?.let { _items.value = it }
                    reloadNames()
                    _messages.emit("Do spíže uloženo položek: ${r.moved ?: 0}.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.emit("Nepodařilo se uložit: ${e.message}")
                }
            }
        }
    }

    /** Put pantry items that ran out on the list: same name, unit and place; usual package size if known. */
    fun addRanOut(list: List<PantryItem>) {
        val entries = suggestionEntries()
        list.forEach { item ->
            val e = entries.firstOrNull { Suggest.norm(it.label) == Suggest.norm(item.name) }
            val qty = if (e != null && e.unit == item.unit && e.qty != null) e.qty!! else 1.0
            shopAdd(Draft(item.name, qty, item.unit, item.category))
        }
    }

    // ---------- recipes ----------
    suspend fun recipeText(slug: String): String = api.recipe(slug)
}
