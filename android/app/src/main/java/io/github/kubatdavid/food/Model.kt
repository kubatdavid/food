package io.github.kubatdavid.food

import kotlinx.serialization.Serializable

// Shapes returned by server/api.php. Stored values (unit, category) are English keys; the UI shows Czech names.

@Serializable
data class PantryItem(
    val id: String,
    val name: String,
    val qty: Double = 0.0,
    val unit: String = "g",
    val category: String = "Other",
    val expires: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ShopItem(
    val id: String,
    val name: String,
    val qty: Double = 0.0,
    val unit: String = "g",
    val category: String = "Other",
    val done: Boolean = false,
)

@Serializable
data class NameUsage(
    val name: String,
    val count: Int = 0,
    val unit: String? = null,
    val qty: Double? = null,
    val category: String? = null,
)

@Serializable
data class RecipeRow(val slug: String, val title: String, val updated: String = "")

@Serializable
data class CatalogEntry(
    val cs: String,
    val en: String = "",
    val unit: String = "g",
    val category: String = "Other",
    val common: Int = 0,
    val size: Double = 0.0,
    val aka: List<String> = emptyList(),
)

@Serializable internal data class ItemsResp(val items: List<PantryItem> = emptyList())
@Serializable internal data class ShopResp(
    val shopping: List<ShopItem> = emptyList(),
    val items: List<PantryItem>? = null,
    val moved: Int? = null,
)
@Serializable internal data class NamesResp(val names: List<NameUsage> = emptyList())
@Serializable internal data class RecipesResp(val recipes: List<RecipeRow> = emptyList())
@Serializable internal data class RecipeResp(val slug: String = "", val text: String = "")
@Serializable internal data class CatalogResp(val ingredients: List<CatalogEntry> = emptyList())
@Serializable internal data class ErrorResp(val error: String = "")
