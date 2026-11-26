package io.github.kubatdavid.food.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kubatdavid.food.FoodViewModel
import io.github.kubatdavid.food.R

@Composable
fun FoodApp(vm: FoodViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val shop by vm.shop.collectAsStateWithLifecycle()
    val openCount = shop.count { !it.done }

    // Fresh data every time the app comes to the front (other devices may have changed it).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.undoables.collect { u ->
            val r = snackbar.showSnackbar(u.text, actionLabel = "Vrátit", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) u.undo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(painterResource(R.drawable.ic_tab_pantry), contentDescription = null) },
                    label = { Text("Spíž") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = {
                        BadgedBox(badge = { if (openCount > 0) Badge { Text("$openCount") } }) {
                            Icon(painterResource(R.drawable.ic_tab_cart), contentDescription = null)
                        }
                    },
                    label = { Text("Nákup") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(painterResource(R.drawable.ic_tab_book), contentDescription = null) },
                    label = { Text("Recepty") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> PantryScreen(vm)
                1 -> ShopScreen(vm)
                else -> RecipesScreen(vm)
            }
        }
    }
}
