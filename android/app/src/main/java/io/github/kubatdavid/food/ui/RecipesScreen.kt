package io.github.kubatdavid.food.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kubatdavid.food.FoodViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(vm: FoodViewModel) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var openSlug by rememberSaveable { mutableStateOf<String?>(null) }

    val slug = openSlug
    if (slug != null) {
        BackHandler { openSlug = null }
        RecipeDetail(vm, slug, recipes.firstOrNull { it.slug == slug }?.title ?: "") { openSlug = null }
        return
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Recepty", style = MaterialTheme.typography.headlineMedium)
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "Nový recept? Napiš v Claude Code: „navrhni recept z mé spíže“. Claude si přečte spíž a recept uloží sem.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (loaded && recipes.isEmpty()) {
                item { EmptyNote("Zatím žádné recepty", "Požádej Claude Code a objeví se tady.") }
            }
            items(recipes, key = { it.slug }) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openSlug = r.slug }
                        .padding(vertical = 14.dp),
                ) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(shortDate(r.updated), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

private fun shortDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d. M."))
}.getOrDefault("")

@Composable
private fun RecipeDetail(vm: FoodViewModel, slug: String, title: String, onBack: () -> Unit) {
    var text by remember(slug) { mutableStateOf<String?>(null) }
    var failed by remember(slug) { mutableStateOf<String?>(null) }
    LaunchedEffect(slug) {
        runCatching { vm.recipeText(slug) }
            .onSuccess { text = it }
            .onFailure { failed = it.message ?: "Recept nelze otevřít." }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Recepty") }
        when {
            failed != null -> ErrorNote(failed!!)
            text == null -> Text("Načítám $title…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Markdown(text!!)
        }
    }
}

/** The small Markdown subset recipes use: # / ## headings, - and 1. lists, **bold** (TM7 settings get a chip look). */
@Composable
private fun Markdown(src: String) {
    val settingStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        background = MaterialTheme.colorScheme.secondaryContainer,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val boldStyle = SpanStyle(fontWeight = FontWeight.SemiBold)
    val settingWords = Regex("(\\d\\s*(s|sec|min)\\b|rychl|speed|°C|Varoma|Turbo)", RegexOption.IGNORE_CASE)

    fun inline(line: String): AnnotatedString = buildAnnotatedString {
        var rest = line
        val bold = Regex("\\*\\*(.+?)\\*\\*")
        while (true) {
            val m = bold.find(rest) ?: break
            append(rest.substring(0, m.range.first))
            val inner = m.groupValues[1]
            withStyle(if (settingWords.containsMatchIn(inner)) settingStyle else boldStyle) { append(inner) }
            rest = rest.substring(m.range.last + 1)
        }
        append(rest.replace("`", ""))
    }

    var number = 0
    src.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> number = 0
            line.startsWith("# ") -> Text(inline(line.removePrefix("# ")), style = MaterialTheme.typography.headlineSmall)
            line.startsWith("#") -> Text(
                inline(line.trimStart('#').trim()).toString().uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            line.startsWith("- ") || line.startsWith("* ") -> Row {
                Text("•", modifier = Modifier.width(18.dp))
                Text(inline(line.substring(2)), style = MaterialTheme.typography.bodyLarge)
            }
            Regex("^\\d+[.)]\\s+").containsMatchIn(line) -> {
                number++
                Row {
                    Text("$number.", modifier = Modifier.width(26.dp), color = MaterialTheme.colorScheme.primary)
                    Text(inline(line.replace(Regex("^\\d+[.)]\\s+"), "")), style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> Text(inline(line), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
