package com.example

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.JournalEntry
import com.example.repository.JournalRepository
import com.example.ui.components.MdBlockAction
import com.example.ui.components.applyMdBlockAction
import com.example.ui.components.detectSlashCommandQuery
import com.example.ui.components.replaceSlashTriggerWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotionWorkspaceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: JournalRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JournalRepository(context, db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun slashCommandDetection_andReplacement_workCleanly() {
        val valAtLineStart = TextFieldValue("/tar", TextRange(4))
        val trigger = detectSlashCommandQuery(valAtLineStart)
        assertNotNull(trigger)
        assertEquals("tar", trigger?.query)

        val replaced = replaceSlashTriggerWith(valAtLineStart, trigger!!, "- [ ] ")
        assertEquals("- [ ] ", replaced.text)
        assertEquals(6, replaced.selection.start)

        // Ignored inside URLs or words without preceding whitespace
        val midWord = TextFieldValue("http://test", TextRange(11))
        assertNull(detectSlashCommandQuery(midWord))
    }

    @Test
    fun markdownBlockActions_toggleNotionBlocksCorrectly() {
        val h1 = applyMdBlockAction(MdBlockAction.H1, TextFieldValue("Roadmap", TextRange(7)))
        assertEquals("# Roadmap", h1.text)

        val todo = applyMdBlockAction(MdBlockAction.TODO, TextFieldValue("Comprar café", TextRange(5)))
        assertEquals("- [ ] Comprar café", todo.text)

        val callout = applyMdBlockAction(MdBlockAction.CALLOUT, TextFieldValue("Idea clave", TextRange(4)))
        assertEquals("💡 Idea clave", callout.text)

        val divider = applyMdBlockAction(MdBlockAction.DIVIDER, TextFieldValue("Texto", TextRange(5)))
        assertEquals("Texto\n---\n", divider.text)
    }

    @Test
    fun workspaceHierarchy_breadcrumbs_checklist_pin_and_statusPersist() = runBlocking {
        val parentId = repository.saveEntry(
            JournalEntry(
                title = "Proyecto Atlas",
                body = "# Objetivo\n- [ ] Definir arquitectura\n- [x] Revisar requisitos",
                iconEmoji = "🚀",
                status = "En curso",
                priority = "Alta"
            )
        )

        val childId = repository.createSubPage(
            parentId = parentId,
            title = "Notas de arquitectura",
            body = "Detalle técnico conectado a [[Proyecto Atlas]]",
            iconEmoji = "🧱",
            status = "Inbox"
        )

        val breadcrumbs = repository.getBreadcrumbs(childId)
        assertEquals(listOf("Proyecto Atlas", "Notas de arquitectura"), breadcrumbs.map { it.title })

        val children = repository.getChildPages(parentId).first()
        assertEquals(1, children.size)
        assertEquals(childId, children.first().entry.id)

        // Interactive checklist line toggle (line 1: "- [ ] Definir arquitectura")
        val toggled = repository.toggleChecklistLine(parentId, 1)
        assertTrue(toggled)
        val updatedParent = db.journalDao().getEntryById(parentId)
        assertNotNull(updatedParent)
        assertTrue(updatedParent!!.body.contains("- [x] Definir arquitectura"))

        // Pin & Kanban status move
        repository.toggleEntryPinned(childId, true)
        repository.updateEntryStatus(childId, "Listo")
        val updatedChild = db.journalDao().getEntryById(childId)
        assertEquals(true, updatedChild?.isPinned)
        assertEquals("Listo", updatedChild?.status)

        kotlinx.coroutines.delay(350)
    }
}
