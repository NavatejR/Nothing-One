package com.nothing.one.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tool parsing is the trust boundary between the 1B model and the user's
 * data: strict parse, garbage in must mean null out, and the chat text must
 * never carry the raw JSON block.
 */
class AssistantToolsTest {

    private val tools = AssistantTools()

    @Test
    fun `parses a create_note tool block`() {
        val raw = """
            Sure, I'll make that note.
            ```json
            {"tool":"create_note","title":"Groceries","body":"oat milk, rye bread"}
            ```
        """.trimIndent()
        val tool = tools.parse(raw)
        assertTrue(tool is AssistantTool.CreateNote)
        tool as AssistantTool.CreateNote
        assertEquals("Groceries", tool.title)
        assertEquals("oat milk, rye bread", tool.body)
    }

    @Test
    fun `parses add_event with defaults`() {
        val raw = """
            ```json
            {"tool":"add_event","title":"Dentist","date":"2026-10-02","time":"14:30","durationMinutes":45}
            ```
        """.trimIndent()
        val tool = tools.parse(raw)
        assertTrue(tool is AssistantTool.AddEvent)
        tool as AssistantTool.AddEvent
        assertEquals("Dentist", tool.title)
        assertEquals("2026-10-02", tool.date)
        assertEquals("14:30", tool.time)
        assertEquals(45L, tool.durationMinutes)
    }

    @Test
    fun `parses start_focus with default minutes when omitted`() {
        val tool = tools.parse("""```json
            {"tool":"start_focus"}
        ```""") as? AssistantTool.StartFocus
        assertEquals(25, tool?.minutes)
    }

    @Test
    fun `uses the last fenced block when several are present`() {
        val raw = """
            ```json
            {"tool":"create_note","title":"first","body":"wrong"}
            ```
            Actually, corrected:
            ```json
            {"tool":"create_note","title":"second","body":"right"}
            ```
        """.trimIndent()
        val tool = tools.parse(raw) as AssistantTool.CreateNote
        assertEquals("second", tool.title)
    }

    @Test
    fun `garbage json yields null instead of crashing`() {
        assertNull(tools.parse("""```json
            {"tool":"create_note","title": not valid json
        ```"""))
    }

    @Test
    fun `no fenced block yields null`() {
        assertNull(tools.parse("Just a plain reply with no tool call."))
    }

    @Test
    fun `unknown tool discriminator yields null`() {
        assertNull(tools.parse("""```json
            {"tool":"delete_everything"}
        ```"""))
    }

    @Test
    fun `unknown extra keys are ignored`() {
        val tool = tools.parse("""```json
            {"tool":"start_focus","minutes":15,"mood":"focused"}
        ```""") as? AssistantTool.StartFocus
        assertEquals(15, tool?.minutes)
    }

    @Test
    fun `stripToolBlock removes every json fence`() {
        val raw = """
            Here is your note.
            ```json
            {"tool":"create_note","title":"x","body":"y"}
            ```
        """.trimIndent()
        val stripped = tools.stripToolBlock(raw)
        assertFalse(stripped.contains("```"))
        assertFalse(stripped.contains("create_note"))
        assertTrue(stripped.contains("Here is your note."))
    }

    @Test
    fun `stripToolBlock of plain text is unchanged`() {
        val text = "A perfectly normal answer."
        assertEquals(text, tools.stripToolBlock(text))
    }

    @Test
    fun `tool instruction teaches all three tools`() {
        val instruction = tools.toolInstruction()
        assertTrue(instruction.contains("create_note"))
        assertTrue(instruction.contains("add_event"))
        assertTrue(instruction.contains("start_focus"))
    }
}
