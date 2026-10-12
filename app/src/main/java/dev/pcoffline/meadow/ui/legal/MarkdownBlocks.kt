package dev.pcoffline.meadow.ui.legal

/** A tiny, dependency-free Markdown parser for the legal documents. Pure, so it's unit-tested. */
object MarkdownBlocks {
    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String) : Block
        data class Table(val rows: List<List<String>>) : Block
    }

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = StringBuilder()
        val table = mutableListOf<List<String>>()

        fun flush() {
            if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
            if (table.isNotEmpty()) blocks += Block.Table(table.toList())
            table.clear()
        }

        for (raw in markdown.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    blocks += Block.Heading(level, line.drop(level).trim())
                }
                line.startsWith("- ") -> {
                    flush()
                    blocks += Block.Bullet(line.drop(2).trim())
                }
                line.startsWith("|") -> {
                    if (paragraph.isNotBlank()) flush()
                    val cells = line.trim('|').split('|').map { it.trim() }
                    // Skip the |---|---| separator row.
                    if (cells.all { it.isNotEmpty() && it.all { c -> c == '-' || c == ':' } }) continue
                    table += cells
                }
                else -> {
                    if (table.isNotEmpty()) flush()
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return blocks
    }

    /** Splits text into (part, isBold) around **bold** markers. */
    fun spans(text: String): List<Pair<String, Boolean>> {
        val parts = text.split("**")
        return parts.mapIndexedNotNull { i, part -> if (part.isEmpty()) null else part to (i % 2 == 1) }
    }
}
