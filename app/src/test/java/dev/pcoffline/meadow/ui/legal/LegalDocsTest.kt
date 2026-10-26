package dev.pcoffline.meadow.ui.legal

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped legal documents, parsed with the same GitHub-flavoured parser the app renders with:
 * well-formed for screen readers, complete, and in sync with the app.
 */
class LegalDocsTest {
    private val dir = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "legal") }.first { it.isDirectory }
    private val parser = MarkdownParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken.NonCancellable)

    // Typed as CharSequence: the String overload is deprecated.
    private fun tree(text: CharSequence) = parser.buildMarkdownTreeFromString(text)

    private fun read(doc: LegalDoc) = File(dir, doc.asset).readText()
    private fun ASTNode.all(): List<ASTNode> = listOf(this) + children.flatMap { it.all() }
    private val headings = listOf(
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6,
    )

    @Test
    fun `every document is shipped and opens with its title`() {
        LegalDoc.entries.forEach { doc ->
            val text = read(doc)
            val first = tree(text).children.first { it.type != org.intellij.markdown.MarkdownTokenTypes.EOL }
            assertEquals("${doc.asset} starts with a top-level heading", MarkdownElementTypes.ATX_1, first.type)
        }
    }

    @Test
    fun `headings never skip a level, so screen readers can navigate them`() {
        LegalDoc.entries.forEach { doc ->
            val levels = tree(read(doc)).all().mapNotNull { headings.indexOf(it.type).takeIf { i -> i >= 0 }?.plus(1) }
            assertEquals("${doc.asset} has exactly one title", 1, levels.count { it == 1 })
            levels.zipWithNext().forEach { (a, b) -> assertTrue("${doc.asset}: h$a followed by h$b", b <= a + 1) }
        }
    }

    @Test
    fun `tables have a header and rows of the same width`() {
        LegalDoc.entries.forEach { doc ->
            val text = read(doc)
            tree(text).all().filter { it.type == GFMElementTypes.TABLE }.forEach { table ->
                val rows = table.children.filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
                assertEquals("${doc.asset}: a table starts with its header", GFMElementTypes.HEADER, rows.first().type)
                val widths = rows.map { row -> row.children.count { it.type == GFMTokenTypes.CELL } }
                assertTrue("${doc.asset}: table rows differ in width $widths", widths.distinct().size == 1)
            }
        }
    }

    @Test
    fun `the privacy notice covers what the app actually does`() {
        val notice = read(LegalDoc.PRIVACY).lowercase()
        listOf("bumblebee", "usage", "accessibility", "github", "delete", "download", "firebase", "photo", "children", "sign-in link")
            .forEach { assertTrue("The privacy notice should mention \"$it\"", it in notice) }
    }

    @Test
    fun `documents carry a version, and the app asks for the current one`() {
        listOf(LegalDoc.PRIVACY, LegalDoc.TERMS).forEach { doc ->
            val version = Regex("""Version (\d+)""").find(read(doc))?.groupValues?.get(1)?.toInt()
            assertEquals("${doc.asset} version", LegalDoc.CURRENT_VERSION, version)
        }
    }

    @Test
    fun `links point somewhere real`() {
        LegalDoc.entries.forEach { doc ->
            val text = read(doc)
            tree(text).all().filter { it.type == MarkdownElementTypes.INLINE_LINK }.forEach { link ->
                val destination = link.children.first { it.type == MarkdownElementTypes.LINK_DESTINATION }.getTextInNode(text).toString()
                assertTrue("${doc.asset}: $destination", destination.startsWith("https://"))
            }
        }
    }

    @Test
    fun `the licences name every library the app ships`() {
        val root = dir.parentFile!!
        // Catalog aliases (androidx-work-runtime) are written as accessors (libs.androidx.work.runtime).
        val groups = File(root, "gradle/libs.versions.toml").readLines().mapNotNull { line ->
            Regex("""^([\w-]+)\s*=\s*\{\s*group\s*=\s*"([^"]+)"""").find(line)?.destructured
        }.associate { (alias, group) -> alias.replace('-', '.') to group }
        val shipped = Regex("""^\s*implementation\((?:platform\()?libs\.([\w.]+)\)""", RegexOption.MULTILINE)
            .findAll(File(root, "app/build.gradle.kts").readText())
            .map { groups.getValue(it.groupValues[1]) }
            .toSet()
        val licences = read(LegalDoc.LICENSES)
        shipped.forEach { group ->
            val name = NAMES.entries.firstOrNull { group.startsWith(it.key) }?.value
            assertNotNull("Add $group to licenses.md and to NAMES", name)
            assertTrue("licenses.md names $name ($group)", name!! in licences)
        }
        TRANSITIVE.forEach { assertTrue("licenses.md names $it", it in licences) }
    }

    private companion object {
        /** Who each group of direct dependencies is credited as in licenses.md. */
        val NAMES = mapOf(
            "androidx." to "Android Jetpack",
            "com.google.firebase" to "Firebase Android SDKs",
            "org.jetbrains.kotlinx" to "kotlinx.coroutines, kotlinx.serialization",
            "io.coil-kt" to "Coil",
            "nl.dionsegijn" to "Konfetti",
            "com.mikepenz" to "Multiplatform Markdown Renderer",
        )

        /** Libraries the direct dependencies bring along. */
        val TRANSITIVE = listOf(
            "JetBrains Markdown", // the Markdown renderer's parser
            "kotlinx.collections.immutable", // the Markdown renderer
            "Okio", // Coil
            "OkHttp", // Coil's network loader and Cloud Functions for Firebase
            "Accompanist Drawable Painter", // Coil's Compose integration
            "gRPC Java", // Cloud Firestore
            "Protocol Buffers", // Cloud Firestore
            "RE2/J", // Cloud Firestore
            "Reactive Streams", // Cloud Functions for Firebase
        )
    }
}
