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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.speech.hotword.HotwordService
import com.example.ui.components.GlassBottomBar
import com.example.ui.components.GlassTab
import com.example.ui.lens.LensScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.EntityExplorerScreen
import com.example.ui.screens.EntryDetailScreen
import com.example.ui.screens.EntryEditScreen
import com.example.ui.screens.GoogleActivityScreen
import com.example.ui.screens.GoogleNotificationsScreen
import com.example.ui.screens.GoogleUniversalSearchScreen
import com.example.ui.screens.HomeDeskScreen
import com.example.ui.screens.KnowledgeGraphScreen
import com.example.ui.screens.NewsBrowserScreen
import com.example.ui.screens.NowBriefHomeScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SettingsAndModelsScreen
import com.example.ui.screens.VaultExplorerScreen
import com.example.ui.screens.VoiceAndAssistantScreen
import com.example.ui.theme.NowBriefTheme
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.MainNavTab
import com.example.wallpaper.NowBriefAppBackground
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface AppScreen {
    data class Main(val tab: MainNavTab) : AppScreen
    data class Edit(val entryId: Long?, val parentEntryId: Long? = null) : AppScreen
    data class Detail(val entryId: Long) : AppScreen
    data object Assistant : AppScreen
    data object Lens : AppScreen
    data object VoiceSettings : AppScreen
    data object Routines : AppScreen
    data object News : AppScreen
}

/**
 * v1.7: encodes the current screen as a Bundle-friendly string so the
 * navigation state survives rotation and process death. Before this, a
 * kill while you read a note dropped you back on Inicio.
 */
private val AppScreenSaver: Saver<AppScreen, String> = Saver(
    save = { screen ->
        when (screen) {
            is AppScreen.Main -> "main:${screen.tab.name}"
            is AppScreen.Edit -> "edit:${screen.entryId ?: -1L}:${screen.parentEntryId ?: -1L}"
            is AppScreen.Detail -> "detail:${screen.entryId}"
            AppScreen.Assistant -> "assistant"
            AppScreen.Lens -> "lens"
            AppScreen.VoiceSettings -> "voicesettings"
            AppScreen.Routines -> "routines"
            AppScreen.News -> "news"
        }
    },
    restore = { value ->
        val parts = value.split(":")
        val arg = parts.getOrNull(1)
        val arg2 = parts.getOrNull(2)
        when (parts.firstOrNull()) {
            "main" -> runCatching { AppScreen.Main(MainNavTab.valueOf(arg ?: "INICIO")) }
                .getOrDefault(AppScreen.Main(MainNavTab.INICIO))
            "edit" -> AppScreen.Edit(
                entryId = arg?.toLongOrNull()?.takeIf { it > 0L },
                parentEntryId = arg2?.toLongOrNull()?.takeIf { it > 0L }
            )
            "detail" -> arg?.toLongOrNull()?.let { AppScreen.Detail(it) }
                ?: AppScreen.Main(MainNavTab.INICIO)
            "assistant" -> AppScreen.Assistant
            "lens" -> AppScreen.Lens
            "voicesettings" -> AppScreen.VoiceSettings
            "routines" -> AppScreen.Routines
            "news" -> AppScreen.News
            else -> AppScreen.Main(MainNavTab.INICIO)
        }
    }
)

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VOICE_COMMAND = "voice_command"

        /** Reminder notifications deep-link the app to the tasks tab. */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_NOTIFICATIONS = "notifications"

        /** Routine notifications deep-link to a full screen (news/assistant). */
        const val EXTRA_OPEN_SCREEN = "open_screen"
        const val SCREEN_NEWS = "news"
        const val SCREEN_ASSISTANT = "assistant"
        const val SCREEN_ROUTINES = "routines"
        const val SCREEN_LENS = "lens"

        /** Routine "note created" notifications deep-link to that note. */
        const val EXTRA_OPEN_DETAIL = "open_detail"

        /** Voice commands that must open the assistant screen (from the
         *  hotword notification). */
        val voiceCommandRequests = MutableStateFlow<String?>(null)

        /** Tab deep-links coming from notifications / the widget. */
        val openTabRequests = MutableStateFlow<String?>(null)

        /** Screen deep-links coming from routine notifications. */
        val openScreenRequests = MutableStateFlow<String?>(null)

        /** Note deep-links coming from routine notifications. */
        val openDetailRequests = MutableStateFlow<Long?>(null)

        /** Shares from other apps (ACTION_SEND), consumed by the UI layer. */
        val shareRequests = MutableStateFlow<android.content.Intent?>(null)

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
        intent?.getStringExtra(EXTRA_OPEN_TAB)?.let { tab ->
            openTabRequests.value = tab
        }
        intent?.getStringExtra(EXTRA_OPEN_SCREEN)?.let { screen ->
            openScreenRequests.value = screen
        }
        val detailId = intent?.getLongExtra(EXTRA_OPEN_DETAIL, -1L) ?: -1L
        if (detailId > 0L) openDetailRequests.value = detailId
    }

    /**
     * Routes system intents: shares (text / links / photos / audio / PDF)
     * land in the journal. NOTE: the system-assistant integration was
     * removed ENTIRELY in v1.5.2 (post-reboot-loop decision) — this app
     * no longer responds to the ASSIST gesture in any way.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                if (shareRequests.value == null) shareRequests.value = intent
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleVoiceCommandIntent(intent)
        handleIncomingIntent(intent)
        // v1.7: Bixby-style routines — "al abrir la app" trigger.
        com.example.routine.RoutineEngine.evaluateAsync(
            this, com.example.routine.RoutineEngine.RoutineEvent.APP_OPEN
        )
        setContent {
            val viewModel: JournalViewModel = viewModel()
            MnemosyneApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceCommandIntent(intent)
        handleIncomingIntent(intent)
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
    // v1.7: navigation survives rotation AND process death (before, a
    // rotation or a kill while reading a note dropped you back on Inicio).
    var screenState by rememberSaveable(stateSaver = AppScreenSaver) {
        mutableStateOf<AppScreen>(AppScreen.Main(MainNavTab.INICIO))
    }
    val currentTab by viewModel.currentTab.collectAsState()
    val realWeather by viewModel.realWeather.collectAsState()
    val darkTheme = isSystemInDarkTheme()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppResumed()
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppPaused()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // After a process-death restore, put the saved tab back into the
    // ViewModel so the Main screen renders what the user was seeing.
    LaunchedEffect(Unit) {
        val saved = screenState
        if (saved is AppScreen.Main && saved.tab != viewModel.currentTab.value) {
            viewModel.selectTab(saved.tab)
        }
    }

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

    // Notification deep-links (task reminders) jump straight to their tab.
    val openTabRequest by MainActivity.openTabRequests.collectAsState()
    LaunchedEffect(openTabRequest) {
        when (openTabRequest) {
            MainActivity.TAB_NOTIFICATIONS -> viewModel.selectTab(MainNavTab.NOTIFICACIONES)
        }
        if (openTabRequest != null) {
            screenState = AppScreen.Main(viewModel.currentTab.value)
            MainActivity.openTabRequests.value = null
        }
    }

    // v1.7: routine deep-links ("abrir las noticias" / "abrir el asistente").
    val openScreenRequest by MainActivity.openScreenRequests.collectAsState()
    LaunchedEffect(openScreenRequest) {
        when (openScreenRequest) {
            MainActivity.SCREEN_NEWS -> screenState = AppScreen.News
            MainActivity.SCREEN_ASSISTANT -> screenState = AppScreen.Assistant
            MainActivity.SCREEN_ROUTINES -> screenState = AppScreen.Routines
            MainActivity.SCREEN_LENS -> screenState = AppScreen.Lens
        }
        if (openScreenRequest != null) {
            MainActivity.openScreenRequests.value = null
        }
    }

    // v1.7: "nota creada por una rutina" deep-links straight to that note.
    val openDetailRequest by MainActivity.openDetailRequests.collectAsState()
    LaunchedEffect(openDetailRequest) {
        openDetailRequest?.let { id ->
            screenState = AppScreen.Detail(id)
            MainActivity.openDetailRequests.value = null
        }
    }

    // Shares from OTHER apps: links, photos, audio, PDFs → journal notes.
    val shareRequest by com.example.MainActivity.shareRequests.collectAsState()
    LaunchedEffect(shareRequest) {
        val intent = shareRequest
        if (intent != null) {
            com.example.MainActivity.shareRequests.value = null
            val summary = try {
                com.example.share.ShareIntakeManager.handle(appContext, intent)
            } catch (_: Exception) {
                null
            }
            if (summary != null) {
                android.widget.Toast.makeText(appContext, summary, android.widget.Toast.LENGTH_LONG).show()
                // Room flows re-emit automatically; no manual refresh needed.
            }
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

    NowBriefTheme(darkTheme = darkTheme) {
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
            is AppScreen.Routines -> {
                BackHandler {
                    screenState = AppScreen.Main(currentTab)
                }
            }
            is AppScreen.News -> {
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

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (screenState !is AppScreen.Lens) {
                    val isHomeTab = screenState is AppScreen.Main &&
                        (currentTab == MainNavTab.INICIO || currentTab == MainNavTab.TIMELINE)
                    NowBriefAppBackground(
                        modifier = Modifier.fillMaxSize(),
                        weatherCode = realWeather.weatherCode,
                        isHomeScreen = isHomeTab
                    )
                }
                AnimatedContent(
                    targetState = screenState,
                    transitionSpec = { fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140)) },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        is AppScreen.Main -> {
                            when (currentTab) {
                                MainNavTab.INICIO -> {
                                    // Artefact-style desk: the whole second brain as
                                    // artefact cards. The previous workspace view
                                    // stays one tab away in Timeline.
                                    HomeDeskScreen(
                                        viewModel = viewModel,
                                        onNavigateToNewEntry = { screenState = AppScreen.Edit(null) },
                                        onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) },
                                        onOpenLens = { screenState = AppScreen.Lens },
                                        onOpenAssistant = { screenState = AppScreen.Assistant }
                                    )
                                }
                                MainNavTab.TIMELINE -> {
                                    NowBriefHomeScreen(
                                        viewModel = viewModel,
                                        onNavigateToNewEntry = { screenState = AppScreen.Edit(null) },
                                        onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) },
                                        onOpenLens = { screenState = AppScreen.Lens },
                                        onOpenAssistant = { screenState = AppScreen.Assistant },
                                        onOpenRoutines = { screenState = AppScreen.Routines },
                                        onOpenNews = { screenState = AppScreen.News }
                                    )
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
                                    onNavigateToGraph = { viewModel.selectTab(MainNavTab.GRAPH) },
                                    onOpenRoutines = { screenState = AppScreen.Routines }
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
                                    onOpenVoiceSettings = { screenState = AppScreen.VoiceSettings },
                                    onOpenRoutines = { screenState = AppScreen.Routines }
                                )
                            }
                        }
                        is AppScreen.Edit -> {
                            EntryEditScreen(
                                entryId = targetScreen.entryId,
                                viewModel = viewModel,
                                onBack = { screenState = AppScreen.Main(currentTab) },
                                onSaved = { savedId -> screenState = AppScreen.Detail(savedId) },
                                onOpenLens = { screenState = AppScreen.Lens },
                                initialParentEntryId = targetScreen.parentEntryId
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
                                onNavigateToEntry = { id -> screenState = AppScreen.Detail(id) },
                                onCreateSubPage = { parentId -> screenState = AppScreen.Edit(null, parentId) }
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
                        is AppScreen.Routines -> {
                            RoutinesScreen(
                                viewModel = viewModel,
                                onBack = { screenState = AppScreen.Main(currentTab) }
                            )
                        }
                        is AppScreen.News -> {
                            NewsBrowserScreen(
                                viewModel = viewModel,
                                isSamsungMode = true,
                                onBack = { screenState = AppScreen.Main(currentTab) },
                                onNavigateToDetail = { id -> screenState = AppScreen.Detail(id) }
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

                // Floating NowBrief liquid-glass capsule dock across all Main tabs
                if (screenState is AppScreen.Main) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
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
 * NowBrief floating liquid-glass capsule navigation bar, centered.
 * Four destinations: Inicio / Buscar / Tareas / Actividad.
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
