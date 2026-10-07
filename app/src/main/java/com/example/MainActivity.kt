package com.example

import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.speech.hotword.HotwordService
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.GlassTab
import com.example.ui.components.GoogleBlue
import com.example.ui.lens.LensScreen
import com.example.ui.screens.AssistantScreen
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
import com.example.ui.screens.VoiceAndAssistantScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NowBriefTheme
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface AppScreen {
    data class Main(val tab: MainNavTab) : AppScreen
    data class Edit(val entryId: Long?) : AppScreen
    data class Detail(val entryId: Long) : AppScreen
    data object Assistant : AppScreen
    data object Lens : AppScreen
    data object VoiceSettings : AppScreen
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VOICE_COMMAND = "voice_command"

        /** Voice commands that must open the assistant screen (from the
         *  hotword notification or the system-assistant session). */
        val voiceCommandRequests = MutableStateFlow<String?>(null)

        /** Whether the app UI is visible — the hotword service uses it to
         *  decide between direct execution and notification hand-off. */
        var isResumed = false
    }

    private fun handleVoiceCommandIntent(intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_VOICE_COMMAND)
            ?: intent?.getStringExtra(HotwordService.EXTRA_VOICE_COMMAND)
        if (!command.isNullOrBlank()) {
            voiceCommandRequests.value = command
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleVoiceCommandIntent(intent)
        setContent {
            val viewModel: JournalViewModel = viewModel()
            MnemosyneApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceCommandIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        HotwordService.appInForeground = true
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
        HotwordService.appInForeground = false
    }
}

@Composable
fun MnemosyneApp(viewModel: JournalViewModel) {
    var screenState by remember { mutableStateOf<AppScreen>(AppScreen.Main(MainNavTab.INICIO)) }
    val currentTab by viewModel.currentTab.collectAsState()
    val interfaceMode by viewModel.interfaceMode.collectAsState()
    val darkTheme = isSystemInDarkTheme()

    // Voice commands arriving from the hotword notification or the
    // system-assistant gesture open the local assistant with the utterance.
    val voiceCommandRequest by MainActivity.voiceCommandRequests.collectAsState()
    LaunchedEffect(voiceCommandRequest) {
        val command = voiceCommandRequest
        if (!command.isNullOrBlank()) {
            screenState = AppScreen.Assistant
            MainActivity.voiceCommandRequests.value = null
        }
    }

    // A hotword exchange that happened while the UI was visible surfaces
    // directly in the assistant chat (command + reply, already executed).
    val hotwordCommand by HotwordService.lastCommand.collectAsState()
    LaunchedEffect(hotwordCommand) {
        if (!hotwordCommand.isNullOrBlank() && MainActivity.isResumed) {
            screenState = AppScreen.Assistant
        }
    }

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
            is AppScreen.Assistant -> {
                BackHandler {
                    screenState = AppScreen.Main(currentTab)
                }
            }
            is AppScreen.Lens -> {
                BackHandler {
                    screenState = AppScreen.Main(currentTab)
                }
            }
            is AppScreen.VoiceSettings -> {
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
                                            onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) },
                                            onOpenLens = { screenState = AppScreen.Lens },
                                            onOpenAssistant = { screenState = AppScreen.Assistant }
                                        )
                                    } else {
                                        TimelineScreen(
                                            viewModel = viewModel,
                                            onNavigateToNewEntry = { screenState = AppScreen.Edit(null) },
                                            onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) },
                                            onNavigateToEntity = { entityId ->
                                                viewModel.selectEntity(entityId)
                                                viewModel.selectTab(MainNavTab.GRAPH)
                                            },
                                            onOpenLens = { screenState = AppScreen.Lens },
                                            onOpenAssistant = { screenState = AppScreen.Assistant }
                                        )
                                    }
                                }
                                MainNavTab.BUSCAR, MainNavTab.VAULT, MainNavTab.SEARCH -> GoogleUniversalSearchScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) },
                                    onOpenVoiceModal = { screenState = AppScreen.Assistant }
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
                                    onBack = { viewModel.selectTab(MainNavTab.INICIO) },
                                    onOpenVoiceSettings = { screenState = AppScreen.VoiceSettings }
                                )
                            }
                        }
                        is AppScreen.Edit -> {
                            EntryEditScreen(
                                entryId = targetScreen.entryId,
                                viewModel = viewModel,
                                onBack = { screenState = AppScreen.Main(currentTab) },
                                onSaved = { savedId -> screenState = AppScreen.Detail(savedId) },
                                onOpenLens = { screenState = AppScreen.Lens }
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
                        is AppScreen.Assistant -> {
                            AssistantScreen(
                                viewModel = viewModel,
                                onBack = { screenState = AppScreen.Main(currentTab) },
                                onOpenLens = { screenState = AppScreen.Lens }
                            )
                        }
                        is AppScreen.VoiceSettings -> {
                            VoiceAndAssistantScreen(
                                viewModel = viewModel,
                                onBack = { screenState = AppScreen.Main(currentTab) }
                            )
                        }
                        is AppScreen.Lens -> {
                            LensScreen(
                                viewModel = viewModel,
                                onBack = { screenState = AppScreen.Main(currentTab) },
                                onNoteSaved = { savedId -> screenState = AppScreen.Detail(savedId) }
                            )
                        }
                    }
                }

                // In Samsung mode the glass capsule floats OVER the content
                // (drawn after it, so it stays on top), horizontally centered.
                // The Scaffold's innerPadding already reserves the nav-bar
                // inset, so no extra windowInsetsPadding here (double inset
                // used to push the dock too high above the gesture bar).
                if (screenState is AppScreen.Main && isSamsungMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
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
 * Samsung mode navigation: the Now brief floating glass capsule, centered.
 * Four destinations for full feature parity with the Google mode dock:
 * Inicio / Buscar / Tareas (notificaciones y recordatorios) / Actividad.
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
        GlassTab(label = "Tareas", icon = Icons.Default.Notifications),
        GlassTab(label = "Actividad", icon = Icons.Default.History)
    )
    val selected = when (currentTab) {
        MainNavTab.INICIO, MainNavTab.TIMELINE -> 0
        MainNavTab.BUSCAR, MainNavTab.VAULT, MainNavTab.SEARCH -> 1
        MainNavTab.NOTIFICACIONES -> 2
        MainNavTab.ACTIVIDAD -> 3
        MainNavTab.GRAPH, MainNavTab.ENTITIES -> 3
        MainNavTab.SETTINGS -> 0
    }
    val tabTargets = listOf(
        MainNavTab.INICIO,
        MainNavTab.BUSCAR,
        MainNavTab.NOTIFICACIONES,
        MainNavTab.ACTIVIDAD
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
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
