package dev.pcoffline.meadow.ui.legal

import dev.pcoffline.meadow.ui.legal.MarkdownBlocks.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkdownBlocksTest {
    @Test
    fun `headings, paragraphs, bullets and tables are recognised`() {
        val blocks = MarkdownBlocks.parse(
            """
            # Title
            First line
            continues here.

            ## Section
            - one
            - **two**

            | What | Who |
            |---|:---:|
            | Name | Bees |
            After the table.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Block.Heading(1, "Title"),
                Block.Paragraph("First line continues here."),
                Block.Heading(2, "Section"),
                Block.Bullet("one"),
                Block.Bullet("**two**"),
                Block.Table(listOf(listOf("What", "Who"), listOf("Name", "Bees"))),
                Block.Paragraph("After the table."),
            ),
            blocks,
        )
    }

    @Test
    fun `bold spans alternate and empty parts are dropped`() {
        assertEquals(listOf("a " to false, "b" to true, " c" to false), MarkdownBlocks.spans("a **b** c"))
        assertEquals(listOf("bold" to true), MarkdownBlocks.spans("**bold**"))
        assertEquals(emptyList<Pair<String, Boolean>>(), MarkdownBlocks.spans(""))
    }

    /** The shipped documents must parse into something sensible: a title and real content. */
    @Test
    fun `every legal document parses with a title and no stray markup`() {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "legal") }.first { it.isDirectory }
        LegalDoc.entries.forEach { doc ->
            val blocks = MarkdownBlocks.parse(File(dir, doc.asset).readText())
            assertTrue("${doc.asset} starts with a title", (blocks.first() as? Block.Heading)?.level == 1)
            assertTrue("${doc.asset} has content", blocks.count { it is Block.Paragraph || it is Block.Bullet } >= 3)
            blocks.filterIsInstance<Block.Table>().forEach { table ->
                assertTrue("${doc.asset} table rows line up", table.rows.all { it.size == table.rows.first().size })
            }
            blocks.forEach { block ->
                val text = when (block) {
                    is Block.Heading -> block.text
                    is Block.Paragraph -> block.text
                    is Block.Bullet -> block.text
                    is Block.Table -> block.rows.flatten().joinToString(" ")
                }
                assertTrue("${doc.asset}: unbalanced bold in \"$text\"", text.split("**").size % 2 == 1)
            }
        }
    }
}
