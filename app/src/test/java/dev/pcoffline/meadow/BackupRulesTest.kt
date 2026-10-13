package dev.pcoffline.meadow

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Nothing Meadow keeps on the phone leaves it through a backup or a transfer to a new phone. */
class BackupRulesTest {
    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }.first { it.isDirectory }

    private fun parse(path: String): Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(main, path)).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    @Test
    fun `the manifest turns off backups and points at the extraction rules`() {
        val application = parse("AndroidManifest.xml").children("application").single()
        assertEquals("false", application.getAttributeNS(ANDROID, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ANDROID, "dataExtractionRules"))
    }

    @Test
    fun `cloud backups and device transfers exclude every domain`() {
        val rules = parse("res/xml/data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val entries = rules.children(section).single().let { it.children("include") + it.children("exclude") }
            assertEquals("$section includes nothing", emptyList<Element>(), entries.filter { it.tagName == "include" })
            val excluded = entries.filter { it.getAttribute("path") == "." }.map { it.getAttribute("domain") }.toSet()
            assertEquals("$section excludes", DOMAINS, excluded)
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
