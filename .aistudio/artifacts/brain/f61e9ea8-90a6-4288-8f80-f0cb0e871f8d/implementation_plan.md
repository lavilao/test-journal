# Mnemosyne: Offline-First Semantic Journal & Personal Knowledge Graph

An offline-first, privacy-centric personal knowledge manager (PKM) and journal for Android that automatically discovers conceptual links, extracts entities, and generates an explainable local knowledge graph without remote servers or cloud LLMs.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following directions were confirmed during interactive clarification and anchor this implementation:

- **Application Name**: **Mnemosyne Semantic Journal** (launcher label: *Mnemosyne*).
- **Core Workflow Balance**: **Balanced hybrid** combining a chronological narrative stream (today's thoughts, calendar timeline) with an ambient semantic intelligence pane (auto-linking, shared entity clusters, concept graph).
- **Visual Aesthetic**: **Warm editorial notebook** featuring soft cream/parchment surfaces, deep espresso and sepia ink typography, subtle terra-cotta/sage semantic badges, and crisp card borders reminiscent of archival stationery.
- **Privacy & Execution Invariant**: Zero telemetry, zero cloud synchronisation, zero generative LLM calls. All information retrieval, TF-IDF scoring, entity normalization, face/image labeling, and translation run 100% on-device.

---

## 1. Overview & Core Concept

Mnemosyne transforms daily freeform journaling into an interconnected personal knowledge graph. As you write, a background pipeline silently extracts people, places, topics, and timestamps, calculating semantic similarity against prior memories using proven Information Retrieval (IR) algorithms (BM25, TF-IDF, token co-occurrence, and ML Kit on-device models).

- **What It Does**: Provides an instantaneous, distraction-free markdown journal editor coupled with a background semantic linking engine that surfaces *why* past notes relate to present reflections (e.g., *"Related because both notes mention Sarah and Project Apollo"*).
- **Target Audience**: Privacy-conscious thinkers, researchers, writers, and daily journalers who want Obsidian/Roam-like semantic connections without cloud leaks, subscription paywalls, or battery-draining local LLM inference.
- **Key Value**: Low-battery, high-performance semantic retrieval that works identically in airplane mode, on older Android 10 devices, or when disconnected from Google Play Services.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Daily Reflection & Instant Capture**:
   - Tap the Floating Action Button or the "Today" header on the Home dashboard.
   - Compose formatted thoughts with optional photo attachments, location tags, and explicit `#tags`.
   - Saving is synchronous and instantaneous (<10ms). The note is persisted directly to SQLite, immediately visible in the timeline.

2. **Ambient Semantic Discovery**:
   - In the background, an asynchronous processing pipeline analyzes the entry's content hash.
   - On opening the entry, the "Connections & Context" sheet reveals:
     - **Extracted Entities**: Categorized chips for People (e.g., *Sarah*), Places (*Starbucks*), Organizations, and Dates.
     - **Automatic Semantic Tags**: Inferred with confidence percentages and explainability reasons (e.g., `#hiking` via keyword and image scene analysis).
     - **Related Memories**: Ranked cards displaying shared entities and conceptual overlap with explicit explanations.

3. **Knowledge Graph & Entity Exploration**:
   - Tap any person, place, or concept chip (or switch to the "Knowledge" tab) to view an interactive visual graph.
   - Nodes represent entities and notes; edges represent weighted semantic co-occurrences.
   - Filter the graph by type (People, Places, Concepts) or temporal slice.

4. **Deep Hybrid Search**:
   - Type queries like `"hiking with Mike"` or `"AI projects"`.
   - Results combine exact FTS5 text matches with entity-expanded semantic links, highlighted with matching excerpts.

5. **On-Device Translation & Language Toolkit**:
   - Select or inspect foreign language entries; translate on-demand using local ML Kit models downloaded only with explicit user permission.

### Visual Identity & Theme (Warm Editorial Notebook)

- **Color Tokens**:
  - `Background / Surface`: Warm cream parchment (`#FBF9F5` light, `#1C1917` deep warm obsidian dark).
  - `Surface Container`: Soft buff / card stock (`#F3EFEA` light, `#292524` dark).
  - `Primary / Ink`: Deep espresso ink (`#2E231D` light, `#F5F0EB` dark).
  - `Semantic Accents`:
    - *People / Social*: Terra-cotta russet (`#B45309`).
    - *Places / Locations*: Muted forest sage (`#2D6A4F`).
    - *Concepts / Topics*: Dusty lapis blue (`#3B6082`).
    - *Automated Insights*: Warm amber ochre (`#C27803`).
- **Typography & Rhythm**:
  - Editorial serif display headers for journal dates and entry titles, paired with high-legibility clean modern body text.
  - Generous 16dp/20dp screen margins, 8dp baseline grid, subtle card borders (`1.dp` solid `#E7DFD5`), and delicate divider lines.
- **Interactive Feedback & States**:
  - Gentle spring animations (`spring(stiffness = Spring.StiffnessMediumLow)`) on card expansion and node selection.
  - Thoughtfully designed empty states with archival sketches, helpful prompts, and non-blocking background task indicators.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: MindForger-Inspired Deterministic IR vs. Heavy On-Device LLM
- **Chosen Approach**: Combine SQLite FTS5 full-text indexing with custom BM25/TF-IDF tokenizers, keyword extraction, and weighted entity overlap scoring.
- **Why**: Zero warm-up latency, negligible RAM (<15MB), zero thermal throttling, and complete explainability.
- **Alternatives Considered**: 3B-parameter quantized LLMs (e.g., Gemma 2B via MediaPipe) were rejected as they require 1.5GB+ RAM, drain 10-15% battery per session, and fail on older 2GB/3GB RAM Android 10 devices.

### Decision 2: Multi-Tier Entity Extraction Architecture
- **Chosen Approach**: Pluggable `EntityExtractor` interface with a primary ML Kit implementation (Entity Extraction & Language Detection) backed by an instant regex/rule-based fallback.
- **Why**: Ensures the app works completely on non-Google Play Services ROMs (e.g., GrapheneOS, microG, AOSP) while leveraging hardware acceleration where available.

### Decision 3: Battery Conservation via Content Hashing & WorkManager
- **Chosen Approach**: Store `contentHash` alongside `processedContentHash`. Background semantic processing only triggers when the hash changes. Lightweight indexing runs immediately in coroutines, while media/graph recalculations are batched in WorkManager with idle/battery-not-low constraints.
- **Why**: Prevents redundant re-indexing during typing and prevents background battery drain.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │  Home / Feed │  │ Journal View │  │ Knowledge    │  │ Hybrid     │  │
│  │  & Timeline  │  │   & Editor   │  │ Graph Canvas │  │ Search     │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └─────┬──────┘  │
└─────────┼─────────────────┼─────────────────┼────────────────┼─────────┘
          │                 │                 │                │
┌─────────▼─────────────────▼─────────────────▼────────────────▼─────────┐
│                           ViewModel Layer                              │
│         JournalViewModel, GraphViewModel, SearchViewModel              │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼──────────────────────────────────────┐
│                           Repository Layer                             │
│   JournalRepository, EntityRepository, RelationshipRepository          │
└──────────────┬──────────────────────────────────────────┬──────────────┘
               │                                          │
┌──────────────▼─────────────┐              ┌─────────────▼──────────────┐
│     Room SQLite Database   │              │   Semantic / ML Pipeline   │
│  - journal_entries         │◄─────────────┤  - Content Hash Detector   │
│  - tags & entry_tags       │              │  - ML Kit / Regex Extractor│
│  - entities & entry_entity │              │  - TF-IDF / BM25 Indexer   │
│  - relationships           │              │  - Explainable Linker      │
│  - media & image_entities  │              │  - Vision / Face Detector  │
│  - entries_fts (FTS5)      │              │  - Offline Translator      │
└────────────────────────────┘              └────────────────────────────┘
```

### Relational Schema Design

1. **`journal_entries`**:
   - `id`: Long primary key.
   - `title`: String.
   - `body`: String (Markdown supported).
   - `createdAt`, `updatedAt`, `journalDate`: Long timestamps.
   - `locationName`, `latitude`, `longitude`: Optional coordinates.
   - `language`: Detected ISO code (e.g., `"en"`).
   - `wordCount`: Int.
   - `contentHash`: String (SHA-256) to track edits.
   - `processedHash`: String (SHA-256) to skip redundant semantic runs.
   - `isEncrypted`: Boolean flag for Keystore-backed PIN protection.

2. **`tags` & `entry_tags`**:
   - Normalised lowercase tag names with `source` (`USER` vs `AUTOMATIC`) and confidence scores.

3. **`entities` & `entry_entities`**:
   - `canonicalName`, `displayName`, and `type` (`PERSON`, `PLACE`, `ORGANIZATION`, `EVENT`, `CONCEPT`, `DATE`).
   - Many-to-many junction recording mention count, exact offsets, and extraction confidence.

4. **`relationships`**:
   - `sourceEntryId`, `targetEntryId`, `relationshipType` (`SIMILAR_TOPIC`, `SHARED_ENTITIES`, `TEMPORAL_PROXIMITY`), `score` (0.0 to 1.0), and `explanation` (e.g., *"Shares entities: Sarah, Starbucks; Topics: AI"*).

5. **`media` & `image_entities`**:
   - Local file URI, dimensions, MIME type, plus extracted labels, scene tags, and face cluster bounds.

6. **`entries_fts`**:
   - SQLite virtual table supporting BM25 token ranking for millisecond-fast searches.

---

## 5. Development Strategy & Milestones

1. **Foundation & Configuration**:
   - Update `app/build.gradle.kts` with Room KSP, ML Kit libraries (Entity Extraction, Language Identification, Translation, Face Detection, Image Labeling), and Coil for media.
   - Update `strings.xml`, `metadata.json`, and application ID.
   - Create custom adaptive app icon and warm editorial theme tokens.

2. **Room Database & Clean Repository**:
   - Implement DAOs, Entities, FTS5 virtual tables, and TypeConverters with migration safety.
   - Construct repositories with reactive Kotlin `Flow` streams.

3. **MindForger-Inspired Semantic Engine**:
   - Build tokenizer, TF-IDF calculation, stopword filtering, and cosine similarity scoring.
   - Implement explainable relationship generator that pairs shared entity mentions with topic proximity.

4. **On-Device Vision & ML Kit Layer**:
   - Abstract `EntityExtractor`, `LanguageDetector`, `TranslationManager`, and `ImageIntelligence` behind clean interfaces with offline fallback handlers.

5. **Jetpack Compose UI & Polish**:
   - **Home / Feed**: Today's prompt, recent reflections, connection insights, and timeline filter.
   - **Editor / Viewer**: Fluid markdown support, image attachment, tags, and inline entity badges.
   - **Knowledge Graph**: 2D force-directed / canvas-rendered concept explorer.
   - **Semantic Search**: Fast search with highlighted snippets and entity filters.
   - **Model & Privacy Settings**: Local data export/import (Markdown + JSON) and model management.

6. **Verification & Testing**:
   - Verify unit tests for TF-IDF calculations, entity normalization, and relationship scoring.
   - Run full app compilation to ensure zero build errors.
