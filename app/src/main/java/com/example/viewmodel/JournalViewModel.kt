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
import com.example.data.model.MediaItem
import com.example.data.model.RelatedEntryDetail
import com.example.data.model.StorageBreakdown
import com.example.data.model.SuggestedTag
import com.example.data.model.Tag
import com.example.media.PlaybackState
import com.example.media.RecordingState
import com.example.media.VoiceJournalManager
import com.example.repository.JournalRepository
import com.example.semantic.MlKitAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainNavTab {
    TIMELINE,
    GRAPH,
    ENTITIES,
    SEARCH,
    SETTINGS
}

data class TranslationUiState(
    val isTranslating: Boolean = false,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "es",
    val translatedText: String? = null,
    val errorMessage: String? = null,
    val isModelDownloaded: Boolean = false
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = JournalRepository(application)

    val currentTab = MutableStateFlow(MainNavTab.TIMELINE)

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

    // Voice Journal Manager
    val voiceManager = VoiceJournalManager(application)
    val recordingState: StateFlow<RecordingState> = voiceManager.recordingState
    val playbackState: StateFlow<PlaybackState> = voiceManager.playbackState
    val currentPlayingPath: StateFlow<String?> = voiceManager.currentPlayingPath
    val isDictating: StateFlow<Boolean> = voiceManager.isDictating

    // Storage Management
    private val _storageBreakdown = MutableStateFlow(StorageBreakdown())
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    val availableTemplates = JournalTemplate.ALL_TEMPLATES

    // Search
    val searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<HybridSearchResult>>(emptyList())
    val searchResults: StateFlow<List<HybridSearchResult>> = _searchResults.asStateFlow()
    val isSearching = MutableStateFlow(false)

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
        voiceManager.stopLiveDictation()
    }

    // Voice Journal Actions
    fun startVoiceRecording() {
        voiceManager.startRecording()
    }

    fun pauseVoiceRecording() {
        voiceManager.pauseRecording()
    }

    fun resumeVoiceRecording() {
        voiceManager.resumeRecording()
    }

    fun stopVoiceRecording(entryId: Long, title: String = "Voice Note") {
        viewModelScope.launch {
            val (file, duration) = voiceManager.stopRecording()
            if (file != null && file.exists()) {
                val filePath = file.absolutePath
                // Insert immediately so audio note is instantly visible
                val recordId = repository.addAudioRecord(
                    entryId = entryId,
                    title = title,
                    filePath = filePath,
                    durationMs = duration,
                    transcript = "",
                    status = "RECORDED"
                )
                selectEntry(entryId)
                // Asynchronously attempt speech recognition
                voiceManager.transcribeAudioOffline { transcript, status ->
                    if (transcript.isNotBlank()) {
                        viewModelScope.launch {
                            repository.updateAudioRecordTranscript(recordId, entryId, transcript, status)
                            selectEntry(entryId)
                        }
                    }
                }
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
}
