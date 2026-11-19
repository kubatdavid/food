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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kubatdavid.food.Cs
import io.github.kubatdavid.food.FoodViewModel
import io.github.kubatdavid.food.ShopItem
import io.github.kubatdavid.food.isOut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(vm: FoodViewModel) {
    val shop by vm.shop.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    val open = shop.filter { !it.done }
    val done = shop.filter { it.done }
    val groups = open.groupBy { it.category }.toSortedMap(compareBy<String>({ Cs.categoryOrder(it) }, { it }))
    val onListNames = open.map { it.name.lowercase() }.toSet()
    val ranOut = items.filter { it.isOut() && it.name.lowercase() !in onListNames }.sortedBy { it.name.lowercase() }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nákupní seznam", style = MaterialTheme.typography.headlineMedium)
                        error?.let { ErrorNote(it) }
                    }
                }
                if (loaded && shop.isEmpty()) {
                    item { EmptyNote("Seznam je prázdný", "Přidej, co chceš koupit, nebo vezmi položky, které ve spíži došly.") }
                }
                groups.forEach { (category, list) ->
                    item(key = "h-$category") { GroupHeader(Cs.category(category), list.size) }
                    items(list.sortedBy { it.name.lowercase() }, key = { it.id }) { s -> ShopRow(s, vm) }
                }
                if (done.isNotEmpty()) {
                    item(key = "h-done") { GroupHeader("V košíku", done.size) }
                    items(done, key = { it.id }) { s -> ShopRow(s, vm) }
                    item(key = "actions") {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = vm::shopToPantry, modifier = Modifier.fillMaxWidth()) {
                                Text("Uložit koupené do spíže (${done.size})")
                            }
                            TextButton(onClick = vm::shopClearDone, modifier = Modifier.fillMaxWidth()) { Text("Smazat odškrtnuté") }
                        }
                    }
                }
                item(key = "ranout-h") {
                    Column(Modifier.padding(top = 24.dp)) {
                        Text("Došlo ve spíži", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (ranOut.isEmpty()) "Nic nedošlo. Položky s množstvím 0 se ukážou tady."
                            else "Položky s množstvím 0. Klepnutím je přidáš na seznam.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (ranOut.size > 1) {
                            OutlinedButton(onClick = { vm.addRanOut(ranOut) }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Přidat vše (${ranOut.size})")
                            }
                        }
                    }
                }
                items(ranOut, key = { "ro-" + it.id }) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(Cs.category(item.category), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.addRanOut(listOf(item)) }) { Text("+ Přidat") }
                    }
                    HorizontalDivider()
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("+ Na seznam") }
    }

    if (adding) {
        ItemSheet(
            title = "Přidat na seznam", initial = null, withExpiry = false, confirmLabel = "Přidat",
            entries = vm::suggestionEntries, onSave = vm::shopAdd, onDelete = null, onDismiss = { adding = false },
        )
    }
}

@Composable
private fun ShopRow(s: ShopItem, vm: FoodViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.shopToggle(s, !s.done) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = s.done, onCheckedChange = { vm.shopToggle(s, it) })
        Text(
            s.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (s.done) TextDecoration.LineThrough else null,
        )
        Text(Cs.amount(s.qty, s.unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { vm.shopDelete(s) }) { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    HorizontalDivider()
}
