package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppInterfaceMode
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.GlassTab
import com.example.ui.components.GoogleBlue
import com.example.ui.screens.EntityExplorerScreen
import com.example.ui.screens.EntryDetailScreen
import com.example.ui.screens.EntryEditScreen
import com.example.ui.screens.GoogleActivityScreen
import com.example.ui.screens.GoogleNotificationsScreen
import com.example.ui.screens.GoogleUniversalSearchScreen
import com.example.ui.screens.KnowledgeGraphScreen
import com.example.ui.screens.NowBriefHomeScreen
import com.example.ui.screens.SettingsAndModelsScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.screens.VaultExplorerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NowBriefTheme
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
            val viewModel: JournalViewModel = viewModel()
            MnemosyneApp(viewModel = viewModel)
        }
    }
}

@Composable
fun MnemosyneApp(viewModel: JournalViewModel) {
    var screenState by remember { mutableStateOf<AppScreen>(AppScreen.Main(MainNavTab.INICIO)) }
    val currentTab by viewModel.currentTab.collectAsState()
    val interfaceMode by viewModel.interfaceMode.collectAsState()
    val darkTheme = isSystemInDarkTheme()

    MnemosyneThemeFor(interfaceMode, darkTheme) {
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
                if (currentTab == MainNavTab.SETTINGS || currentTab == MainNavTab.GRAPH || currentTab == MainNavTab.ENTITIES) {
                    BackHandler {
                        viewModel.selectTab(MainNavTab.INICIO)
                    }
                }
            }
        }

        val isSamsungMode = interfaceMode == AppInterfaceMode.SAMSUNG

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (screenState is AppScreen.Main && !isSamsungMode) {
                    GoogleNavBar(
                        currentTab = currentTab,
                        onSelect = { viewModel.selectTab(it) }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = screenState,
                    transitionSpec = { fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140)) },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        is AppScreen.Main -> {
                            when (currentTab) {
                                MainNavTab.INICIO, MainNavTab.TIMELINE -> {
                                    if (isSamsungMode) {
                                        NowBriefHomeScreen(
                                            viewModel = viewModel,
                                            onNavigateToNewEntry = { screenState = AppScreen.Edit(null) },
                                            onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) }
                                        )
                                    } else {
                                        TimelineScreen(
                                            viewModel = viewModel,
                                            onNavigateToNewEntry = { screenState = AppScreen.Edit(null) },
                                            onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) },
                                            onNavigateToEntity = { entityId ->
                                                viewModel.selectEntity(entityId)
                                                viewModel.selectTab(MainNavTab.GRAPH)
                                            }
                                        )
                                    }
                                }
                                MainNavTab.BUSCAR, MainNavTab.VAULT, MainNavTab.SEARCH -> GoogleUniversalSearchScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) }
                                )
                                MainNavTab.NOTIFICACIONES -> GoogleNotificationsScreen(
                                    viewModel = viewModel
                                )
                                MainNavTab.ACTIVIDAD -> GoogleActivityScreen(
                                    viewModel = viewModel,
                                    onNavigateToGraph = { viewModel.selectTab(MainNavTab.GRAPH) }
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
                                MainNavTab.SETTINGS -> SettingsAndModelsScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.selectTab(MainNavTab.INICIO) }
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

                // In Samsung mode the glass capsule floats OVER the content
                // (drawn after it, so it stays on top).
                if (screenState is AppScreen.Main && isSamsungMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .align(Alignment.BottomCenter)
                    ) {
                        NowBriefGlassNavBar(
                            currentTab = currentTab,
                            onSelect = { viewModel.selectTab(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Applies the theme that matches the selected interface mode.
 */
@Composable
private fun MnemosyneThemeFor(
    mode: AppInterfaceMode,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    when (mode) {
        AppInterfaceMode.SAMSUNG -> NowBriefTheme(darkTheme = darkTheme, content = content)
        AppInterfaceMode.GOOGLE -> MyApplicationTheme(darkTheme = darkTheme, content = content)
    }
}

/**
 * Samsung mode navigation: the Now brief floating glass capsule.
 */
@Composable
private fun NowBriefGlassNavBar(
    currentTab: MainNavTab,
    onSelect: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        GlassTab(label = "Inicio", icon = Icons.Default.Home),
        GlassTab(label = "Buscar", icon = Icons.Default.Search),
        GlassTab(label = "Actividad", icon = Icons.Default.History),
        GlassTab(label = "Ajustes", icon = Icons.Default.Settings)
    )
    val selected = when (currentTab) {
        MainNavTab.INICIO, MainNavTab.TIMELINE -> 0
        MainNavTab.BUSCAR, MainNavTab.VAULT, MainNavTab.SEARCH -> 1
        MainNavTab.NOTIFICACIONES, MainNavTab.ACTIVIDAD -> 2
        MainNavTab.GRAPH, MainNavTab.ENTITIES -> 2
        MainNavTab.SETTINGS -> 3
    }
    val tabTargets = listOf(
        MainNavTab.INICIO,
        MainNavTab.BUSCAR,
        MainNavTab.ACTIVIDAD,
        MainNavTab.SETTINGS
    )

    Box(
        modifier = modifier
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassBottomBar(
            tabs = tabs,
            selectedTabIndex = selected,
            onTabSelected = { index -> onSelect(tabTargets[index]) }
        )
    }
}

/**
 * Google mode navigation: the classic full-width Material bar.
 */
@Composable
private fun GoogleNavBar(
    currentTab: MainNavTab,
    onSelect: (MainNavTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("main_navigation_bar")
    ) {
        NavigationBarItem(
            selected = currentTab == MainNavTab.INICIO || currentTab == MainNavTab.TIMELINE,
            onClick = { onSelect(MainNavTab.INICIO) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Inicio", modifier = Modifier.size(24.dp)) },
            label = { Text("Inicio") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GoogleBlue,
                indicatorColor = GoogleBlue.copy(alpha = 0.15f)
            ),
            modifier = Modifier.testTag("nav_inicio")
        )
        NavigationBarItem(
            selected = currentTab == MainNavTab.BUSCAR || currentTab == MainNavTab.VAULT || currentTab == MainNavTab.SEARCH,
            onClick = { onSelect(MainNavTab.BUSCAR) },
            icon = { Icon(Icons.Default.Search, contentDescription = "Buscar", modifier = Modifier.size(24.dp)) },
            label = { Text("Buscar") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GoogleBlue,
                indicatorColor = GoogleBlue.copy(alpha = 0.15f)
            ),
            modifier = Modifier.testTag("nav_buscar")
        )
        NavigationBarItem(
            selected = currentTab == MainNavTab.NOTIFICACIONES,
            onClick = { onSelect(MainNavTab.NOTIFICACIONES) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Notificaciones", modifier = Modifier.size(24.dp)) },
            label = { Text("Notificaciones") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GoogleBlue,
                indicatorColor = GoogleBlue.copy(alpha = 0.15f)
            ),
            modifier = Modifier.testTag("nav_notificaciones")
        )
        NavigationBarItem(
            selected = currentTab == MainNavTab.ACTIVIDAD || currentTab == MainNavTab.GRAPH,
            onClick = { onSelect(MainNavTab.ACTIVIDAD) },
            icon = { Icon(Icons.Default.History, contentDescription = "Actividad", modifier = Modifier.size(24.dp)) },
            label = { Text("Actividad") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = GoogleBlue,
                indicatorColor = GoogleBlue.copy(alpha = 0.15f)
            ),
            modifier = Modifier.testTag("nav_actividad")
        )
    }
}
