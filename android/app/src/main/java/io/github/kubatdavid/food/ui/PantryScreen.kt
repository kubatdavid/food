package io.github.kubatdavid.food.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kubatdavid.food.Cs
import io.github.kubatdavid.food.Draft
import io.github.kubatdavid.food.FoodViewModel
import io.github.kubatdavid.food.PantryItem
import io.github.kubatdavid.food.isOut
import io.github.kubatdavid.food.isSoon

private enum class Filter(val label: String) { ALL("Vše"), SOON("Spotřebovat brzy"), OUT("Došlo") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(vm: FoodViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    val view = items
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter {
            when (filter) {
                Filter.ALL -> true
                Filter.SOON -> it.isSoon()
                Filter.OUT -> it.isOut()
            }
        }
    val groups = view.groupBy { it.category }.toSortedMap(compareBy<String>({ Cs.categoryOrder(it) }, { it }))

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Domácí spíž", style = MaterialTheme.typography.headlineMedium)
                        if (loaded) {
                            Text(
                                "${items.count { !it.isOut() }} skladem · ${items.count { it.isSoon() }} spotřebovat brzy · ${items.count { it.isOut() }} došlo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            label = { Text("Hledat položky") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(Filter.entries) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                        }
                        error?.let { ErrorNote(it) }
                    }
                }
                if (loaded && items.isEmpty() && error == null) {
                    item { EmptyNote("Spíž je prázdná", "Klepni na Přidat a zadej, co máš doma.") }
                } else if (loaded && view.isEmpty() && items.isNotEmpty()) {
                    item { EmptyNote("Nic neodpovídá", "Vymaž hledání nebo zvol Vše.") }
                }
                groups.forEach { (category, list) ->
                    item(key = "h-$category") { GroupHeader(Cs.category(category), list.size) }
                    items(list.sortedBy { it.name.lowercase() }, key = { it.id }) { item ->
                        PantryRow(
                            item,
                            onMinus = { vm.setQty(item, item.qty - Cs.step(item.unit)) },
                            onPlus = { vm.setQty(item, item.qty + Cs.step(item.unit)) },
                            onClick = { editingId = item.id },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("+ Přidat") }
    }

    if (adding) {
        ItemSheet(
            title = "Přidat do spíže", initial = null, withExpiry = true, confirmLabel = "Přidat",
            entries = vm::suggestionEntries, onSave = vm::addItem, onDelete = null, onDismiss = { adding = false },
        )
    }
    val editing = items.firstOrNull { it.id == editingId }
    if (editing != null) {
        ItemSheet(
            title = "Upravit: ${editing.name}",
            initial = Draft(editing.name, editing.qty, editing.unit, editing.category, editing.expires),
            withExpiry = true, confirmLabel = "Uložit",
            entries = vm::suggestionEntries,
            onSave = { vm.updateItem(editing.id, it) },
            onDelete = { vm.deleteItem(editing) },
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun PantryRow(item: PantryItem, onMinus: () -> Unit, onPlus: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isOut()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.isOut()) TextDecoration.LineThrough else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.isOut()) Tag("Došlo", bad = true)
                ExpiryTag(item.expires)
            }
        }
        FilledTonalIconButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            Cs.amount(item.qty, item.unit),
            modifier = Modifier.widthIn(min = 72.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalIconButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun ExpiryTag(iso: String?) {
    val d = Cs.daysUntil(iso) ?: return
    when {
        d < 0 -> Tag("Prošlé", bad = true)
        d == 0L -> Tag("Spotřebovat dnes", warn = true)
        d == 1L -> Tag("Spotřebovat zítra", warn = true)
        d <= 3 -> Tag("Spotřebovat do $d dnů", warn = true)
        else -> Tag("do ${Cs.date(iso)}")
    }
}

@Composable
fun Tag(text: String, warn: Boolean = false, bad: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        bad -> scheme.errorContainer to scheme.onErrorContainer
        warn -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun GroupHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun EmptyNote(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorNote(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(12.dp)) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
