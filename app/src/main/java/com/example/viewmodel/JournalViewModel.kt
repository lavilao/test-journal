package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AudioRecordItem
import com.example.data.model.AutolinkSpan
import com.example.data.model.EntityItem
import com.example.data.model.EntityWithEntries
import com.example.data.model.EntryWithRelations
import com.example.data.model.EventItem
import com.example.data.model.FacePersonAssociation
import com.example.data.model.HybridSearchResult
import com.example.data.model.JournalEntry
import com.example.data.model.JournalPage
import com.example.data.model.JournalTemplate
import com.example.data.model.KnowledgeGraphData
import com.example.data.model.LocalReminder
import com.example.contacts.DeviceContactInfo
import com.example.data.AppInterfaceMode
import com.example.data.AppModePreferences
import com.example.data.CalendarSyncManager
import com.example.data.DeviceCalendarEvent
import com.example.data.DeviceFileInfo
import com.example.data.DeviceSearchManager
import com.example.data.RecentAppUsageInfo
import com.example.data.model.MediaItem
import com.example.rss.RssArticle
import com.example.rss.RssFeedManager
import com.example.rss.RssFeedSource
import com.example.telemetry.DeviceLifeHubManager
import com.example.telemetry.LifeHubTelemetry
import com.example.telemetry.RealWeatherData
import com.example.telemetry.WeatherCity
import com.example.telemetry.WeatherService
import com.example.data.model.RelatedEntryDetail
import com.example.data.model.StorageBreakdown
import com.example.data.model.SuggestedTag
import com.example.data.model.Tag
import com.example.media.PlaybackState
import com.example.media.RecordingState
import com.example.media.VoiceJournalManager
import com.example.repository.JournalRepository
import com.example.semantic.MlKitAnalyzer
import com.example.semantic.SemanticSearchEngine
import com.example.semantic.SemanticSearchUiState
import com.example.sync.ReminderNotifications
import com.example.sync.SyncHub
import com.example.sync.SyncScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainNavTab {
    INICIO,
    BUSCAR,
    NOTIFICACIONES,
    ACTIVIDAD,
    TIMELINE,
    VAULT,
    GRAPH,
    ENTITIES,
    SEARCH,
    SETTINGS
}

enum class VaultFileType {
    ALL,
    NOTES,
    AUDIO,
    PHOTOS
}

data class VaultFileItem(
    val id: Long,
    val title: String,
    val fileName: String,
    val fileExtension: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val lastModified: Long,
    val tags: List<Tag>,
    val mood: String?,
    val imageUri: String?,
    val audioDurationMs: Long?,
    val previewText: String,
    val category: String
)

data class TranslationUiState(
    val isTranslating: Boolean = false,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "es",
    val translatedText: String? = null,
    val errorMessage: String? = null,
    val isModelDownloaded: Boolean = false
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    val repository = JournalRepository(application)

    // Speech engine manager: exposes the recognition engines installed on
    // the device (Google Speech Services / Samsung Voice Input / on-device)
    // and the offline-model status & download APIs (Android 13+).
    val speechEngineManager = com.example.speech.SpeechEngineManager(application)

    // Voice Journal Manager (must be declared before its dependent flows)
    val voiceManager = VoiceJournalManager(application, speechEngineManager)
    val recordingState: StateFlow<RecordingState> = voiceManager.recordingState
    val playbackState: StateFlow<PlaybackState> = voiceManager.playbackState
    val currentPlayingPath: StateFlow<String?> = voiceManager.currentPlayingPath
    val isDictating: StateFlow<Boolean> = voiceManager.isDictating

    // On-Device speech availability (honest system state, no fake downloads)
    val isDictationAvailable: Boolean get() = voiceManager.isDictationAvailable
    val hasOnDeviceRecognizer: Boolean get() = voiceManager.hasOnDeviceRecognizer

    val liveRecordingTranscript: StateFlow<String> = voiceManager.liveTranscript
    val partialTranscript: StateFlow<String> = voiceManager.partialTranscript

    val currentTab = MutableStateFlow(MainNavTab.INICIO)

    val entries: StateFlow<List<EntryWithRelations>> = repository.allEntriesWithRelations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entities: StateFlow<List<EntityItem>> = repository.allEntities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<Tag>> = repository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<EventItem>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val forgottenThreads: StateFlow<List<EntityItem>> = repository.getForgottenThreads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _onThisDayMemories = MutableStateFlow<List<JournalEntry>>(emptyList())
    val onThisDayMemories: StateFlow<List<JournalEntry>> = _onThisDayMemories.asStateFlow()

    // Device Health & Life Hub Telemetry
    val lifeHubManager = DeviceLifeHubManager(application, viewModelScope)
    val telemetry: StateFlow<LifeHubTelemetry> = lifeHubManager.telemetry

    // Local Reminders
    val allReminders: StateFlow<List<LocalReminder>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeReminders: StateFlow<List<LocalReminder>> = repository.activeReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val nextActiveReminder: StateFlow<LocalReminder?> = repository.nextActiveReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // RSS Discover Feed
    val rssFeedManager = RssFeedManager(application)
    val rssArticles: StateFlow<List<RssArticle>> = rssFeedManager.articles
    val isRssLoading: StateFlow<Boolean> = rssFeedManager.isLoading
    val rssSources: StateFlow<List<RssFeedSource>> = rssFeedManager.sources

    init {
        viewModelScope.launch {
            rssFeedManager.refreshFeeds()
        }
    }

    fun refreshRssFeeds() {
        viewModelScope.launch {
            rssFeedManager.refreshFeeds()
        }
    }

    fun addCustomRssFeed(url: String, name: String, category: String = "Custom") {
        viewModelScope.launch {
            rssFeedManager.addCustomFeed(url, name, category)
        }
    }

    // Weather Service (Real Live Weather from Open-Meteo & GPS)
    val weatherService = WeatherService(application)
    val realWeather: StateFlow<RealWeatherData> = weatherService.weatherState

    // Calendar Sync Manager (Real Android Device Calendar)
    val calendarSyncManager = CalendarSyncManager(application)

    // Shared with the widget + the 5-minute background sync, and guaranteed
    // FUTURE-ONLY: finished events drop off in real time (60s filter tick
    // while the app is open, 5-minute background refresh otherwise).
    val upcomingCalendarEvents: StateFlow<List<DeviceCalendarEvent>> = SyncHub.calendarEvents

    // Device Search & Recent Usage (Contacts, MediaStore Files, UsageStats)
    val deviceSearchManager = DeviceSearchManager(application)
    private val _deviceContactsResults = MutableStateFlow<List<DeviceContactInfo>>(emptyList())
    val deviceContactsResults: StateFlow<List<DeviceContactInfo>> = _deviceContactsResults.asStateFlow()

    private val _deviceFilesResults = MutableStateFlow<List<DeviceFileInfo>>(emptyList())
    val deviceFilesResults: StateFlow<List<DeviceFileInfo>> = _deviceFilesResults.asStateFlow()

    private val _recentDeviceFiles = MutableStateFlow<List<DeviceFileInfo>>(emptyList())
    val recentDeviceFiles: StateFlow<List<DeviceFileInfo>> = _recentDeviceFiles.asStateFlow()

    private val _recentDeviceApps = MutableStateFlow<List<RecentAppUsageInfo>>(emptyList())
    val recentDeviceApps: StateFlow<List<RecentAppUsageInfo>> = _recentDeviceApps.asStateFlow()

    // ------------------------------------------------------------------
    // Habit engine: on-device telemetry collectors + learned routines
    // ------------------------------------------------------------------
    private val _habitDigest = MutableStateFlow<com.example.habit.HabitMiners.HabitDigest?>(null)
    val habitDigest: StateFlow<com.example.habit.HabitMiners.HabitDigest?> = _habitDigest.asStateFlow()

    /** Harvest (throttled) + rebuild the learned-routines digest. */
    fun refreshHabitFacts() {
        viewModelScope.launch {
            try {
                com.example.habit.HabitEngine.collectTick(getApplication<Application>())
            } catch (_: Exception) {
            }
            _habitDigest.value = try {
                com.example.habit.HabitMiners.buildDigest(getApplication<Application>())
            } catch (_: Exception) {
                null
            }
        }
    }

    // App Interface Mode (Google vs Samsung NowBrief)
    val appModePreferences = AppModePreferences(application)
    val interfaceMode: StateFlow<AppInterfaceMode> = appModePreferences.interfaceMode

    fun setInterfaceMode(mode: AppInterfaceMode) {
        appModePreferences.setMode(mode)
    }

    fun refreshWeather(force: Boolean = false) {
        viewModelScope.launch {
            weatherService.refreshWeather(force)
        }
    }

    /** Minutes since the last real weather reading (for staleness captions). */
    fun weatherAgeMinutes(): Int = weatherService.cachedAgeMinutes()

    // Weather city selection
    val selectedWeatherCity: StateFlow<WeatherCity?> = weatherService.selectedCity

    fun searchWeatherCities(query: String, onDone: (List<WeatherCity>) -> Unit = {}) {
        viewModelScope.launch {
            val results = weatherService.searchCities(query)
            _citySearchResults.value = results
            onDone(results)
        }
    }

    private val _citySearchResults = MutableStateFlow<List<WeatherCity>>(emptyList())
    val citySearchResults: StateFlow<List<WeatherCity>> = _citySearchResults.asStateFlow()

    fun selectWeatherCity(city: WeatherCity) {
        weatherService.setSelectedCity(city)
        viewModelScope.launch {
            weatherService.refreshWeather(force = true)
        }
    }

    fun clearWeatherCity() {
        weatherService.clearSelectedCity()
        viewModelScope.launch {
            weatherService.refreshWeather()
        }
    }

    fun openSystemWeatherApp() {
        weatherService.openSystemWeatherApp()
    }

    fun refreshCalendarEvents() {
        viewModelScope.launch {
            SyncHub.refreshCalendar(getApplication<Application>())
        }
    }

    /** Full background-style pass, used by pull-to-refresh. */
    fun syncNow() {
        SyncHub.syncNow(getApplication<Application>())
        refreshTelemetry()
        refreshRecentActivity()
        refreshHabitFacts()
    }

    fun refreshRecentActivity() {
        viewModelScope.launch {
            _recentDeviceFiles.value = deviceSearchManager.getRecentDeviceFiles(15)
            _recentDeviceApps.value = deviceSearchManager.getRecentlyUsedApps(10)
        }
    }

    fun searchDevice(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _deviceContactsResults.value = emptyList()
                _deviceFilesResults.value = emptyList()
            } else {
                _deviceContactsResults.value = deviceSearchManager.searchContacts(query)
                _deviceFilesResults.value = deviceSearchManager.searchFiles(query)
            }
        }
    }

    fun openDeviceFile(file: DeviceFileInfo) {
        deviceSearchManager.openFile(file.uri, file.mimeType)
    }

    fun launchDeviceApp(packageName: String) {
        deviceSearchManager.launchApp(packageName)
    }

    // TagSpaces Smart Vault States
    val vaultTypeFilter = MutableStateFlow(VaultFileType.ALL)
    val vaultTagFilter = MutableStateFlow<String?>(null)
    val vaultCategoryFilter = MutableStateFlow<String?>(null)
    val vaultSearchQuery = MutableStateFlow("")

    val vaultItems: StateFlow<List<VaultFileItem>> = combine(
        entries,
        vaultTypeFilter,
        vaultTagFilter,
        vaultCategoryFilter,
        vaultSearchQuery
    ) { all, typeFilter, tagFilter, categoryFilter, search ->
        val mapped = all.map { entryWithRel ->
            val entry = entryWithRel.entry
            val itemTags = entryWithRel.tags
            val hasAudio = entryWithRel.audioRecords.isNotEmpty()
            val hasPhoto = entry.imageUri != null || entryWithRel.mediaItems.isNotEmpty()

            val ext = when {
                hasAudio -> "m4a"
                hasPhoto -> "jpg"
                else -> "md"
            }
            val cleanTitle = entry.title.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9_]"), "_").take(22)
            val fileName = "${cleanTitle}.$ext"
            val estimatedSize = when (ext) {
                "m4a" -> 1024L * 180 + (entry.body.length * 2L)
                "jpg" -> 1024L * 850
                else -> (entry.title.length + entry.body.length) * 2L + 256L
            }
            val formattedSize = when {
                estimatedSize >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", estimatedSize / (1024f * 1024f))
                else -> "${estimatedSize / 1024} KB"
            }

            val cat = when {
                itemTags.any { it.name.contains("health", ignoreCase = true) || it.name.contains("fitness", ignoreCase = true) || it.name.contains("walk", ignoreCase = true) } -> "Health"
                itemTags.any { it.name.contains("finance", ignoreCase = true) || it.name.contains("tax", ignoreCase = true) || it.name.contains("legal", ignoreCase = true) || it.name.contains("budget", ignoreCase = true) } -> "Finance"
                itemTags.any { it.name.contains("project", ignoreCase = true) || it.name.contains("work", ignoreCase = true) || it.name.contains("home", ignoreCase = true) || it.name.contains("dev", ignoreCase = true) } -> "Projects"
                else -> "Personal"
            }

            VaultFileItem(
                id = entry.id,
                title = entry.title.ifBlank { "Untitled" },
                fileName = fileName,
                fileExtension = ext,
                sizeBytes = estimatedSize,
                formattedSize = formattedSize,
                lastModified = entry.updatedAt,
                tags = itemTags,
                mood = entry.mood,
                imageUri = entry.imageUri ?: entryWithRel.mediaItems.firstOrNull()?.uri,
                audioDurationMs = entryWithRel.audioRecords.firstOrNull()?.durationMs,
                previewText = entry.body.take(120),
                category = cat
            )
        }

        mapped.filter { item ->
            val matchesType = when (typeFilter) {
                VaultFileType.ALL -> true
                VaultFileType.NOTES -> item.fileExtension == "md"
                VaultFileType.AUDIO -> item.fileExtension == "m4a"
                VaultFileType.PHOTOS -> item.fileExtension == "jpg"
            }
            val matchesTag = tagFilter == null || item.tags.any { it.name.equals(tagFilter, ignoreCase = true) }
            val matchesCategory = categoryFilter == null || item.category.equals(categoryFilter, ignoreCase = true)
            val matchesSearch = search.isBlank() || item.title.contains(search, ignoreCase = true) || item.previewText.contains(search, ignoreCase = true)

            matchesType && matchesTag && matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Entry for Detail / Edit
    private val _selectedEntryId = MutableStateFlow<Long?>(null)
    val selectedEntryId: StateFlow<Long?> = _selectedEntryId.asStateFlow()

    private val _selectedEntryDetail = MutableStateFlow<EntryWithRelations?>(null)
    val selectedEntryDetail: StateFlow<EntryWithRelations?> = _selectedEntryDetail.asStateFlow()

    private val _relatedEntries = MutableStateFlow<List<RelatedEntryDetail>>(emptyList())
    val relatedEntries: StateFlow<List<RelatedEntryDetail>> = _relatedEntries.asStateFlow()

    private val _autolinks = MutableStateFlow<List<AutolinkSpan>>(emptyList())
    val autolinks: StateFlow<List<AutolinkSpan>> = _autolinks.asStateFlow()

    private val _suggestedTags = MutableStateFlow<List<SuggestedTag>>(emptyList())
    val suggestedTags: StateFlow<List<SuggestedTag>> = _suggestedTags.asStateFlow()

    // Selected Entity
    private val _selectedEntityDetail = MutableStateFlow<EntityWithEntries?>(null)
    val selectedEntityDetail: StateFlow<EntityWithEntries?> = _selectedEntityDetail.asStateFlow()

    private val _selectedEntityMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val selectedEntityMedia: StateFlow<List<MediaItem>> = _selectedEntityMedia.asStateFlow()

    // Knowledge Graph
    private val _graphData = MutableStateFlow(KnowledgeGraphData())
    val graphData: StateFlow<KnowledgeGraphData> = _graphData.asStateFlow()
    val isGraphLoading = MutableStateFlow(false)

    // Storage Management
    private val _storageBreakdown = MutableStateFlow(StorageBreakdown())
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    val availableTemplates = JournalTemplate.ALL_TEMPLATES

    // Search
    val searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<HybridSearchResult>>(emptyList())
    val searchResults: StateFlow<List<HybridSearchResult>> = _searchResults.asStateFlow()
    val isSearching = MutableStateFlow(false)

    // Semantic note search (lexical + optional Needle 3 re-ranking).
    private val _semanticSearch = MutableStateFlow(SemanticSearchUiState())
    val semanticSearch: StateFlow<SemanticSearchUiState> = _semanticSearch.asStateFlow()

    private var semanticSearchJob: Job? = null

    /** Debounced semantic search over the journal (local AI when available). */
    fun onSemanticSearchChanged(query: String) {
        semanticSearchJob?.cancel()
        if (query.isBlank()) {
            _semanticSearch.value = SemanticSearchUiState()
            return
        }
        semanticSearchJob = viewModelScope.launch {
            delay(250)
            _semanticSearch.value = _semanticSearch.value.copy(query = query, isSearching = true)
            _semanticSearch.value = SemanticSearchEngine.search(
                query, entries.value, getApplication()
            )
        }
    }

    // Translation
    val translationState = MutableStateFlow(TranslationUiState())

    // Timeline Filter
    val timelineFilterTag = MutableStateFlow<String?>(null)
    val timelineFilterEntity = MutableStateFlow<String?>(null)

    val isRebuildingMetadata = MutableStateFlow(false)

    val filteredEntries: StateFlow<List<EntryWithRelations>> = combine(
        entries,
        timelineFilterTag,
        timelineFilterEntity
    ) { all, tagFilter, entityFilter ->
        all.filter { item ->
            val matchTag = tagFilter == null || item.tags.any { it.name.equals(tagFilter, ignoreCase = true) }
            val matchEntity = entityFilter == null || item.entities.any { it.displayName.equals(entityFilter, ignoreCase = true) }
            matchTag && matchEntity
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            refreshGraph()
            refreshOnThisDay()
            refreshWeather()
            refreshCalendarEvents()
            refreshRecentActivity()
        }
        // Boot the realtime layer: persisted snapshot + 5-minute cadence +
        // the per-reminder wake-up alarm. Survives reboots via the receiver.
        SyncHub.ensureInitialized(application)
        SyncScheduler.schedule(application)
        SyncScheduler.scheduleNextReminderAlarm(application)
        viewModelScope.launch {
            ReminderNotifications.checkAndNotifyDue(application)
        }
        // Habit engine: first harvest (7-day bootstrap on first run) + digest.
        refreshHabitFacts()
        // Real-time filter: every minute, past events vanish and the list is
        // re-queried so a task/event created outside this app shows up fast.
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                SyncHub.refreshFilter()
                SyncHub.refreshCalendar(application)
            }
        }
    }

    fun refreshOnThisDay() {
        viewModelScope.launch {
            _onThisDayMemories.value = repository.getOnThisDayMemories()
        }
    }

    fun selectTab(tab: MainNavTab) {
        currentTab.value = tab
        if (tab == MainNavTab.GRAPH) {
            refreshGraph()
        }
    }

    fun setTagFilter(tag: String?) {
        timelineFilterTag.value = tag
    }

    fun setEntityFilter(entity: String?) {
        timelineFilterEntity.value = entity
    }

    fun clearFilters() {
        timelineFilterTag.value = null
        timelineFilterEntity.value = null
    }

    fun selectEntry(id: Long) {
        _selectedEntryId.value = id
        viewModelScope.launch {
            repository.getEntryWithRelations(id).collect {
                _selectedEntryDetail.value = it
            }
        }
        viewModelScope.launch {
            repository.getRelatedEntries(id).collect {
                _relatedEntries.value = it
            }
        }
        viewModelScope.launch {
            _autolinks.value = repository.getAutolinksForEntry(id)
        }
        viewModelScope.launch {
            repository.getSuggestedTagsForEntry(id).collect {
                _suggestedTags.value = it
            }
        }
    }

    fun selectEntity(entityId: Long) {
        viewModelScope.launch {
            repository.getEntityWithEntries(entityId).collect {
                _selectedEntityDetail.value = it
            }
        }
        viewModelScope.launch {
            repository.getMediaForPerson(entityId).collect {
                _selectedEntityMedia.value = it
            }
        }
    }

    fun clearSelectedEntity() {
        _selectedEntityDetail.value = null
        _selectedEntityMedia.value = emptyList()
    }

    fun clearSelectedEntry() {
        _selectedEntryId.value = null
        _selectedEntryDetail.value = null
        _relatedEntries.value = emptyList()
        _autolinks.value = emptyList()
        _suggestedTags.value = emptyList()
        translationState.value = TranslationUiState()
    }

    fun saveEntry(
        id: Long = 0,
        title: String,
        body: String,
        journalDate: Long = System.currentTimeMillis(),
        mood: String? = null,
        location: String? = null,
        manualTags: List<String> = emptyList(),
        attachedImageUri: Uri? = null,
        pages: List<JournalPage> = emptyList(),
        audioRecords: List<AudioRecordItem> = emptyList(),
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val entry = JournalEntry(
                id = id,
                title = title.trim(),
                body = body.trim(),
                journalDate = journalDate,
                mood = mood,
                location = location?.trim()?.ifBlank { null }
            )
            val savedId = repository.saveEntry(entry, manualTags, attachedImageUri, pages, audioRecords)
            refreshGraph()
            refreshOnThisDay()
            onComplete(savedId)
        }
    }

    // Voice Dictation & Transcription
    fun startDictation(onResult: (String) -> Unit, onError: () -> Unit = {}) {
        voiceManager.startLiveDictation(onResult, onError)
    }

    fun stopDictation() {
        voiceManager.stopDictation()
    }

    // -----------------------------------------------------------------
    // Local Assistant (Google Assistant replacement, on-device only)
    // -----------------------------------------------------------------

    val assistantManager = com.example.assistant.AssistantManager(application)

    /** Short, honest engine status line for the assistant UI/settings. */
    fun dictationEngineDescription(): String = voiceManager.dictationEngineDescription()

    /** Storage permission state observed by the search UIs. */
    private val _hasStoragePermission = MutableStateFlow(deviceSearchManager.hasStoragePermission())
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    fun refreshStoragePermission() {
        _hasStoragePermission.value = deviceSearchManager.hasStoragePermission()
    }

    fun requiredStoragePermissions(): Array<String> = deviceSearchManager.requiredStoragePermissions()

    // Voice Journal Actions
    fun startVoiceRecording() {
        voiceManager.startRecording()
    }

    /** Start a voice note with REAL live dictation running in parallel. */
    fun startVoiceRecordingWithDictation() {
        voiceManager.startRecordingWithLiveDictation()
    }

    fun pauseVoiceRecording() {
        voiceManager.pauseRecording()
    }

    fun resumeVoiceRecording() {
        voiceManager.resumeRecording()
    }

    fun stopVoiceRecording(entryId: Long, title: String = "Voice Note") {
        viewModelScope.launch {
            // Capture the transcript BEFORE stopping (stop ends the dictation session).
            val transcript = voiceManager.consumeLiveTranscript()
            val (file, duration) = voiceManager.stopRecording()
            if (file != null && file.exists()) {
                val filePath = file.absolutePath
                val status = if (transcript.isNotBlank()) "COMPLETED" else "RECORDED"
                repository.addAudioRecord(
                    entryId = entryId,
                    title = title,
                    filePath = filePath,
                    durationMs = duration,
                    transcript = transcript,
                    status = status
                )
                selectEntry(entryId)
            }
        }
    }

    fun updateAudioTranscript(audioId: Long, entryId: Long, transcript: String) {
        viewModelScope.launch {
            repository.updateAudioRecordTranscript(audioId, entryId, transcript, "COMPLETED")
            selectEntry(entryId)
        }
    }

    fun addAudioRecord(
        entryId: Long,
        title: String,
        filePath: String,
        durationMs: Long,
        transcript: String,
        status: String = "COMPLETED",
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addAudioRecord(entryId, title, filePath, durationMs, transcript, status)
            selectEntry(entryId)
            onDone()
        }
    }

    fun createQuickVoiceMemory(title: String, transcript: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveEntry(
                entry = com.example.data.model.JournalEntry(
                    title = title.ifBlank { "Spoken Reflection" },
                    body = transcript,
                    mood = "Thoughtful",
                    createdAt = System.currentTimeMillis()
                )
            )
            selectEntry(id)
            onCreated(id)
        }
    }

    fun playAudio(path: String) {
        voiceManager.startPlayback(path)
    }

    fun pauseAudio() {
        voiceManager.pausePlayback()
    }

    fun resumeAudio() {
        voiceManager.resumePlayback()
    }

    fun stopAudio() {
        voiceManager.stopPlayback()
    }

    fun deleteAudio(audioId: Long, entryId: Long) {
        viewModelScope.launch {
            repository.deleteAudioRecord(audioId, entryId)
            selectEntry(entryId)
        }
    }

    fun persistImageToLocalStorage(uri: Uri): String = repository.persistImageToLocalStorage(uri)

    // Photo Album Actions
    fun addPhoto(entryId: Long, uri: Uri, caption: String = "") {
        viewModelScope.launch {
            repository.addPhotoToEntry(entryId, uri, caption)
            selectEntry(entryId)
        }
    }

    fun updatePhotoCaption(mediaId: Long, caption: String, entryId: Long) {
        viewModelScope.launch {
            repository.updatePhotoCaption(mediaId, caption)
            selectEntry(entryId)
        }
    }

    fun deletePhoto(mediaId: Long, entryId: Long) {
        viewModelScope.launch {
            repository.deletePhoto(mediaId)
            selectEntry(entryId)
        }
    }

    // Multi-page Actions
    fun savePage(page: JournalPage, entryId: Long) {
        viewModelScope.launch {
            repository.savePage(page)
            selectEntry(entryId)
        }
    }

    fun deletePage(pageId: Long, entryId: Long) {
        viewModelScope.launch {
            repository.deletePage(pageId, entryId)
            selectEntry(entryId)
        }
    }



    fun removeTagFromEntry(entryId: Long, tagId: Long) {
        viewModelScope.launch {
            // Room flows re-emit automatically — re-selecting the entry here
            // stacked duplicate collectors and made the whole screen reload.
            repository.removeTagFromEntry(entryId, tagId)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
            clearSelectedEntry()
            refreshGraph()
            refreshOnThisDay()
        }
    }

    fun acceptSuggestedTag(suggestedTag: SuggestedTag) {
        viewModelScope.launch {
            repository.acceptSuggestedTag(suggestedTag)
        }
    }

    fun dismissSuggestedTag(suggestedTagId: Long) {
        viewModelScope.launch {
            // In-place removal: the suggested-tags flow re-emits by itself;
            // the old full re-select reloaded the entire screen.
            repository.dismissSuggestedTag(suggestedTagId)
        }
    }

    fun associateFaceWithPerson(mediaId: Long, faceIndex: Int, personEntityId: Long) {
        viewModelScope.launch {
            repository.associateFaceWithPerson(mediaId, faceIndex, personEntityId)
        }
    }

    fun rebuildAllSemanticMetadata(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            isRebuildingMetadata.value = true
            repository.rebuildAllSemanticMetadata()
            refreshGraph()
            refreshOnThisDay()
            isRebuildingMetadata.value = false
            onComplete()
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            isSearching.value = true
            _searchResults.value = repository.searchHybrid(query)
            isSearching.value = false
        }
    }

    fun refreshGraph() {
        viewModelScope.launch {
            isGraphLoading.value = true
            _graphData.value = repository.getKnowledgeGraphData()
            isGraphLoading.value = false
        }
    }

    fun checkTranslationModel(targetLang: String) {
        viewModelScope.launch {
            val downloaded = MlKitAnalyzer.isModelDownloaded(targetLang)
            translationState.value = translationState.value.copy(
                targetLanguage = targetLang,
                isModelDownloaded = downloaded
            )
        }
    }

    fun translateEntry(entryText: String, sourceLang: String = "en", targetLang: String) {
        viewModelScope.launch {
            translationState.value = translationState.value.copy(
                isTranslating = true,
                targetLanguage = targetLang,
                errorMessage = null
            )
            val result = MlKitAnalyzer.translateText(entryText, sourceLang, targetLang)
            result.fold(
                onSuccess = { translated ->
                    translationState.value = translationState.value.copy(
                        isTranslating = false,
                        translatedText = translated,
                        isModelDownloaded = true
                    )
                },
                onFailure = { error ->
                    translationState.value = translationState.value.copy(
                        isTranslating = false,
                        errorMessage = error.localizedMessage ?: "Translation failed"
                    )
                }
            )
        }
    }

    fun downloadTranslationModel(targetLang: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = MlKitAnalyzer.downloadTranslationModel(targetLang)
            if (success) {
                checkTranslationModel(targetLang)
            }
            onDone(success)
        }
    }

    fun refreshStorageBreakdown() {
        viewModelScope.launch {
            _storageBreakdown.value = repository.getStorageBreakdown()
        }
    }

    fun clearOcrCache(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearOcrCache()
            refreshStorageBreakdown()
            onCleared()
        }
    }

    suspend fun getExportJson(): String = repository.exportToJson()

    suspend fun getExportMarkdown(): String = repository.exportToMarkdownBundle()

    fun addReminder(title: String, category: String = "Personal", dueTimestamp: Long = System.currentTimeMillis() + 3600_000) {
        viewModelScope.launch {
            repository.saveReminder(LocalReminder(title = title, category = category, dueTimestamp = dueTimestamp))
            lifeHubManager.refreshTelemetry()
            SyncScheduler.scheduleNextReminderAlarm(getApplication<Application>())
        }
    }

    fun toggleReminder(id: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.setReminderCompleted(id, isCompleted)
            lifeHubManager.refreshTelemetry()
            SyncScheduler.scheduleNextReminderAlarm(getApplication<Application>())
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminder(id)
            lifeHubManager.refreshTelemetry()
            SyncScheduler.scheduleNextReminderAlarm(getApplication<Application>())
        }
    }

    fun refreshTelemetry() {
        lifeHubManager.refreshTelemetry()
    }

    override fun onCleared() {
        super.onCleared()
        lifeHubManager.unregisterStepSensor()
    }
}
