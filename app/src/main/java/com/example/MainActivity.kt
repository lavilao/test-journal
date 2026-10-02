package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.FolderOpen
import com.example.ui.screens.EntityExplorerScreen
import com.example.ui.screens.EntryDetailScreen
import com.example.ui.screens.EntryEditScreen
import com.example.ui.screens.HybridSearchScreen
import com.example.ui.screens.KnowledgeGraphScreen
import com.example.ui.screens.SettingsAndModelsScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.screens.VaultExplorerScreen
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab

sealed interface AppScreen {
    data class Main(val tab: MainNavTab) : AppScreen
    data class Edit(val entryId: Long?) : AppScreen
    data class Detail(val entryId: Long) : AppScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: JournalViewModel = viewModel()
                MnemosyneApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MnemosyneApp(viewModel: JournalViewModel) {
    var screenState by remember { mutableStateOf<AppScreen>(AppScreen.Main(MainNavTab.TIMELINE)) }
    val currentTab by viewModel.currentTab.collectAsState()

    // Handle system back button for sub-screens
    when (val screen = screenState) {
        is AppScreen.Edit -> {
            BackHandler {
                screenState = AppScreen.Main(currentTab)
            }
        }
        is AppScreen.Detail -> {
            BackHandler {
                screenState = AppScreen.Main(currentTab)
            }
        }
        is AppScreen.Main -> {
            // Default system back behavior
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (screenState is AppScreen.Main) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.TIMELINE,
                        onClick = { viewModel.selectTab(MainNavTab.TIMELINE) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Inicio", modifier = Modifier.size(22.dp)) },
                        label = { Text("Inicio") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = ForestPrimary
                        ),
                        modifier = Modifier.testTag("nav_timeline")
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.VAULT,
                        onClick = { viewModel.selectTab(MainNavTab.VAULT) },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Buscar", modifier = Modifier.size(22.dp)) },
                        label = { Text("Buscar") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = ForestPrimary
                        ),
                        modifier = Modifier.testTag("nav_vault")
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.GRAPH,
                        onClick = { viewModel.selectTab(MainNavTab.GRAPH) },
                        icon = { Icon(Icons.Default.History, contentDescription = "Actividad", modifier = Modifier.size(22.dp)) },
                        label = { Text("Actividad") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = ForestPrimary
                        ),
                        modifier = Modifier.testTag("nav_graph")
                    )
                    NavigationBarItem(
                        selected = currentTab == MainNavTab.SETTINGS,
                        onClick = { viewModel.selectTab(MainNavTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes", modifier = Modifier.size(22.dp)) },
                        label = { Text("Ajustes") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = ForestPrimary
                        ),
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedContent(
                targetState = screenState,
                transitionSpec = { fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140)) },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    is AppScreen.Main -> {
                        when (currentTab) {
                            MainNavTab.TIMELINE -> TimelineScreen(
                                viewModel = viewModel,
                                onNavigateToNewEntry = { screenState = AppScreen.Edit(null) },
                                onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) },
                                onNavigateToEntity = { entityId ->
                                    viewModel.selectEntity(entityId)
                                    viewModel.selectTab(MainNavTab.ENTITIES)
                                }
                            )
                            MainNavTab.VAULT -> VaultExplorerScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) }
                            )
                            MainNavTab.GRAPH -> KnowledgeGraphScreen(
                                viewModel = viewModel,
                                onNavigateToEntry = { id -> screenState = AppScreen.Detail(id) },
                                onNavigateToEntity = { entityId ->
                                    viewModel.selectEntity(entityId)
                                    viewModel.selectTab(MainNavTab.ENTITIES)
                                }
                            )
                            MainNavTab.ENTITIES -> EntityExplorerScreen(
                                viewModel = viewModel,
                                onNavigateToEntry = { id -> screenState = AppScreen.Detail(id) }
                            )
                            MainNavTab.SEARCH -> HybridSearchScreen(
                                viewModel = viewModel,
                                onNavigateToEntry = { id -> screenState = AppScreen.Detail(id) }
                            )
                            MainNavTab.SETTINGS -> SettingsAndModelsScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                    is AppScreen.Edit -> {
                        EntryEditScreen(
                            entryId = targetScreen.entryId,
                            viewModel = viewModel,
                            onBack = { screenState = AppScreen.Main(currentTab) },
                            onSaved = { savedId -> screenState = AppScreen.Detail(savedId) }
                        )
                    }
                    is AppScreen.Detail -> {
                        EntryDetailScreen(
                            entryId = targetScreen.entryId,
                            viewModel = viewModel,
                            onBack = { screenState = AppScreen.Main(currentTab) },
                            onEdit = { id -> screenState = AppScreen.Edit(id) },
                            onNavigateToEntity = { entityId ->
                                viewModel.selectEntity(entityId)
                                screenState = AppScreen.Main(MainNavTab.ENTITIES)
                            },
                            onNavigateToEntry = { id -> screenState = AppScreen.Detail(id) }
                        )
                    }
                }
            }
        }
    }
}
