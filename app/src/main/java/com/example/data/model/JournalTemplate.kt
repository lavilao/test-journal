package com.example.data.model

data class JournalTemplate(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val defaultTitle: String,
    val defaultBody: String,
    val defaultTags: List<String> = emptyList(),
    val defaultMood: String? = null
) {
    companion object {
        val ALL_TEMPLATES = listOf(
            JournalTemplate(
                id = "freeform",
                name = "Freeform Note",
                description = "Empty page for spontaneous thoughts and ideas.",
                iconName = "edit",
                defaultTitle = "",
                defaultBody = ""
            ),
            JournalTemplate(
                id = "daily_reflection",
                name = "Daily Journal",
                description = "Morning intentions, evening reflections, and gratitude.",
                iconName = "wb_sunny",
                defaultTitle = "Daily Reflection",
                defaultBody = """
### Morning Intentions
- What is my primary focus for today?
- What am I grateful for this morning?

### Evening Reflection
- What went well today?
- What did I learn or discover?
- Highlight of the day:
                """.trimIndent(),
                defaultTags = listOf("reflection", "daily"),
                defaultMood = "Peaceful"
            ),
            JournalTemplate(
                id = "weekly_review",
                name = "Weekly Review",
                description = "High-level review of wins, progress, and upcoming goals.",
                iconName = "calendar_view_week",
                defaultTitle = "Weekly Synthesis",
                defaultBody = """
### Big Wins & Accomplishments
1. 
2. 

### Key Lessons & Blockers
- 

### People & Connections
- Meaningful conversations this week:

### Next Week's Priorities
- [ ] 
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("review", "planning")
            ),
            JournalTemplate(
                id = "travel_trip",
                name = "Travel & Trip Day",
                description = "Destinations, transit details, meals, and adventures.",
                iconName = "flight",
                defaultTitle = "Travel Day: ",
                defaultBody = """
### Itinerary & Places
- Location / Destination:
- Sights & Landmarks:

### Food & Atmosphere
- Memorable meals or cafes:

### Impressions & Highlights
- 
                """.trimIndent(),
                defaultTags = listOf("travel", "places")
            ),
            JournalTemplate(
                id = "book_note",
                name = "Book Notes",
                description = "Author, central thesis, quotes, and personal takeaways.",
                iconName = "menu_book",
                defaultTitle = "Book: ",
                defaultBody = """
**Author:** 
**Category:** Non-Fiction / Fiction
**Rating:** ★★★★☆

### Central Thesis
- 

### Key Quotes & Passages
> 

### Personal Takeaways & Action Items
- 
                """.trimIndent(),
                defaultTags = listOf("reading", "book")
            ),
            JournalTemplate(
                id = "movie_media",
                name = "Movie / Show",
                description = "Director, themes, memorable moments, and personal critique.",
                iconName = "movie",
                defaultTitle = "Watch: ",
                defaultBody = """
**Director / Creator:** 
**Watched on:** 
**Rating:** 8/10

### Synopsis & Themes
- 

### Memorable Moments & Performances
- 

### Final Impressions
- 
                """.trimIndent(),
                defaultTags = listOf("media", "cinema")
            ),
            JournalTemplate(
                id = "game_review",
                name = "Game Log",
                description = "Platform, gameplay highlights, story impressions, and hours played.",
                iconName = "sports_esports",
                defaultTitle = "Game: ",
                defaultBody = """
**Platform:** 
**Status:** In Progress / Completed

### Gameplay & Mechanics
- 

### Atmosphere & Art / Music
- 

### Memorable Milestones
- 
                """.trimIndent(),
                defaultTags = listOf("gaming", "review")
            ),
            JournalTemplate(
                id = "project_milestone",
                name = "Project Tracker",
                description = "Project goals, architectural decisions, and current backlog.",
                iconName = "work",
                defaultTitle = "Project: ",
                defaultBody = """
### Objective & Context
- Why are we building this?

### Decisions & Architecture
- 

### Next Deliverables
- [ ] 
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("project", "focus")
            ),
            JournalTemplate(
                id = "meeting_notes",
                name = "Meeting Notes",
                description = "Attendees, agenda items, discussion notes, and action items.",
                iconName = "groups",
                defaultTitle = "Meeting: ",
                defaultBody = """
**Participants:** 
**Date/Time:** 

### Agenda
1. 

### Discussion Points
- 

### Action Items & Next Steps
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("meeting", "work")
            ),
            JournalTemplate(
                id = "idea_spark",
                name = "Idea Spark",
                description = "Unfiltered brainstorm, potential value, and experiment steps.",
                iconName = "lightbulb",
                defaultTitle = "Idea: ",
                defaultBody = """
### The Spark
- 

### Why It Matters
- 

### Potential Next Experiment
- 
                """.trimIndent(),
                defaultTags = listOf("idea", "brainstorm")
            )
        )
    }
}
