package io.github.kubatdavid.food.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.kubatdavid.food.Cs
import io.github.kubatdavid.food.Draft
import io.github.kubatdavid.food.Suggest
import io.github.kubatdavid.food.Suggestion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add / edit sheet shared by the pantry and the shopping list.
 * Typing a name shows ranked suggestions; tapping one (or the keyboard's Next) fills
 * unit, place and the usual package size, then jumps to the amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(
    title: String,
    initial: Draft?,
    withExpiry: Boolean,
    confirmLabel: String,
    entries: () -> List<Suggest.Entry>,
    onSave: (Draft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var qty by remember { mutableStateOf(initial?.let { Cs.qty(it.qty) } ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "g") }
    var category by remember { mutableStateOf(initial?.category ?: if (withExpiry) "Pantry" else "Other") }
    var expires by remember { mutableStateOf(initial?.expires) }
    var showSuggestions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val catalog = remember { entries() }
    val suggestions = if (showSuggestions) Suggest.suggest(name, catalog) else emptyList()
    val parsedQty = Cs.parseQty(qty)

    fun pick(s: Suggestion) {
        name = s.label
        unit = s.unit
        category = if (s.category in Cs.categories) s.category else "Other"
        if (initial == null && s.qty != null) qty = Cs.qty(s.qty)
        showSuggestions = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; showSuggestions = true },
                label = { Text("Položka") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    val top = suggestions.firstOrNull()
                    if (top != null) pick(top) else showSuggestions = false
                }),
            )
            if (suggestions.isNotEmpty()) {
                Column {
                    suggestions.forEach { s ->
                        SuggestionRow(s, name) { pick(s) }
                        HorizontalDivider()
                    }
                }
            }

            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                label = { Text("Množství (${Cs.unit(unit)})") },
                singleLine = true,
                isError = qty.isNotBlank() && parsedQty == null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )

            Text("Jednotka", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Cs.units) { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(Cs.unit(u)) }) }
            }
            Text("Umístění", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Cs.categories) { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(Cs.category(c)) }) }
            }

            if (withExpiry) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate = true }) {
                        Text(if (expires.isNullOrBlank()) "Spotřebovat do…" else "Spotřebovat do ${Cs.date(expires)}")
                    }
                    if (!expires.isNullOrBlank()) TextButton(onClick = { expires = null }) { Text("Bez data") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (onDelete != null) {
                    if (confirmDelete) {
                        Text("Opravdu odebrat?", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onDelete(); onDismiss() }) { Text("Odebrat", color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("Odebrat", color = MaterialTheme.colorScheme.error) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = name.isNotBlank() && parsedQty != null,
                    onClick = {
                        onSave(Draft(name.trim().replace(Regex("\\s+"), " "), parsedQty ?: 0.0, unit, category, expires))
                        onDismiss()
                    },
                ) { Text(confirmLabel) }
            }
        }
    }

    if (pickDate) {
        val initialMillis = expires?.let { runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { expires = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Zrušit") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, typed: String, onClick: () -> Unit) {
    val q = Suggest.norm(typed.trim())
    val n = Suggest.norm(s.label)
    val at = n.indexOf(q).takeIf { q.isNotEmpty() && it >= 0 && it + q.length <= s.label.length }
    val label = buildAnnotatedString {
        if (at == null) append(s.label) else {
            append(s.label.substring(0, at))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                append(s.label.substring(at, at + q.length))
            }
            append(s.label.substring(at + q.length))
        }
        if (s.via.isNotEmpty()) withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · ${s.via}") }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (s.count > 0) Text("${s.count}× přidáno", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (s.qty?.let { Cs.amount(it, s.unit) } ?: Cs.unit(s.unit)) + " · " + Cs.category(s.category),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
