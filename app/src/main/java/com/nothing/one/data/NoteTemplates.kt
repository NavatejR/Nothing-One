package com.nothing.one.data

/**
 * Starter templates for new notes. Bodies are plain Markdown — checklist
 * items use `- [ ]` so the editor's checkbox handling works out of the box.
 */
data class NoteTemplate(
    val id: String,
    val title: String,
    val body: String,
    val isChecklist: Boolean,
)

object NoteTemplates {

    val Blank = NoteTemplate(
        id = "blank",
        title = "",
        body = "",
        isChecklist = false,
    )

    val ShoppingList = NoteTemplate(
        id = "shopping",
        title = "Shopping list",
        body = """
            # Shopping list

            - [ ] Produce
            - [ ] Pantry staples
            - [ ] Coffee
            - [ ] Something for dinner
        """.trimIndent(),
        isChecklist = true,
    )

    val TodoList = NoteTemplate(
        id = "todo",
        title = "To-do",
        body = """
            # To-do

            ## Today

            - [ ] First task
            - [ ] Second task

            ## Later

            - [ ] Someday thing
        """.trimIndent(),
        isChecklist = true,
    )

    val Gratitude = NoteTemplate(
        id = "gratitude",
        title = "Gratitude",
        body = """
            # Gratitude

            Three good things:

            - **One** —
            - **Two** —
            - **Three** —
        """.trimIndent(),
        isChecklist = false,
    )

    val Meeting = NoteTemplate(
        id = "meeting",
        title = "Meeting notes",
        body = """
            # Meeting — *date*

            **With:**

            ## Agenda

            -

            ## Notes

            ## Action items

            - [ ]
        """.trimIndent(),
        isChecklist = false,
    )

    val JournalPrompt = NoteTemplate(
        id = "journal-prompt",
        title = "Journal prompt",
        body = """
            # What mattered today?

            > One question, answered honestly.

            The one thing I keep thinking about is…
        """.trimIndent(),
        isChecklist = false,
    )

    val All = listOf(Blank, ShoppingList, TodoList, Gratitude, Meeting, JournalPrompt)

    fun byId(id: String?): NoteTemplate = All.firstOrNull { it.id == id } ?: Blank
}
