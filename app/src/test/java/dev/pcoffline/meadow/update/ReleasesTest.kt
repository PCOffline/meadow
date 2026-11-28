package dev.pcoffline.meadow.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasesTest {
    private fun release(
        tag: String = "v1.2.0",
        assets: String = """[{"name":"meadow-1.2.0.apk","browser_download_url":"https://x/meadow.apk","size":42,"digest":"sha256:ABC123"}]""",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = Releases.parse(
        """{"tag_name":"$tag","name":"Spring","body":" New petals \n","html_url":"https://github.com/r","draft":$draft,
           "prerelease":$prerelease,"assets":$assets,"author":{"login":"someone"},"unknown_field":1}""",
    )

    @Test
    fun `versions parse with or without v, patch and pre-release`() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(1, 2, 0), Version.parse("1.2"))
        assertEquals(Version(2, 0, 0, "beta.1"), Version.parse("2.0.0-beta.1"))
        assertNull(Version.parse("latest"))
        assertNull(Version.parse("1"))
        assertNull(Version.parse("v1.2.3.4"))
    }

    @Test
    fun `versions compare numerically and releases beat their pre-releases`() {
        val ordered = listOf("0.9.0", "1.0.0-alpha", "1.0.0-beta", "1.0.0", "1.0.1", "1.2.0", "1.10.0", "2.0.0").map { Version.parse(it)!! }
        assertEquals(ordered, ordered.shuffled(java.util.Random(7)).sorted())
    }

    @Test
    fun `a newer release with an apk is offered, with its checksum`() {
        val update = Releases.updateFrom(release(), current = "1.1.9")
        assertNotNull(update)
        update!!
        assertEquals(Version(1, 2, 0), update.version)
        assertEquals("https://x/meadow.apk", update.apkUrl)
        assertEquals("abc123", update.sha256)
        assertEquals("• New petals", update.notes)
        assertEquals(42L, update.sizeBytes)
    }

    @Test
    fun `the notes keep what changed for people, and leave out the rest`() {
        val generated = """
            ## What's Changed
            * build(deps-dev): Bump oxlint from 1.89.0 to 1.90.0 in /backend/functions by @dependabot[bot] in https://github.com/PCOffline/meadow/pull/63
            * feat(chat): open at what's new, and stay there while content loads by @PCOffline in https://github.com/PCOffline/meadow/pull/102
            * fix(app)!: keep rain, snow and clouds inside the scene by @PCOffline in https://github.com/PCOffline/meadow/pull/127
            * ci: run the e2e journeys in shards by @PCOffline in https://github.com/PCOffline/meadow/pull/132
            * Rest screen: read the usage log afresh once per showing by @PCOffline in https://github.com/PCOffline/meadow/pull/92
            * perf(enforcement): ask for content changes only while Meadow reads them by @PCOffline in https://github.com/PCOffline/meadow/pull/130

            ## New Contributors
            * @sam made their first contribution in https://github.com/PCOffline/meadow/pull/1

            **Full Changelog**: https://github.com/PCOffline/meadow/compare/v0.5.1...v0.5.2
        """.trimIndent()
        assertEquals(
            listOf(
                "• Open at what's new, and stay there while content loads",
                "• Keep rain, snow and clouds inside the scene",
                "• Rest screen: read the usage log afresh once per showing",
                "• Ask for content changes only while Meadow reads them",
            ),
            Releases.notesForPeople(generated).lines(),
        )
    }

    @Test
    fun `notes with nothing for people are empty, and notes written by hand are kept`() {
        val onlyUpkeep = """
            ## What's Changed
            * build(deps): Bump gradle/actions/setup-gradle from 6.4.0 to 6.4.1 by @dependabot[bot] in https://github.com/PCOffline/meadow/pull/65
            * docs: update README screenshots by @github-actions[bot] in https://github.com/PCOffline/meadow/pull/66

            **Full Changelog**: https://github.com/PCOffline/meadow/compare/v0.5.0...v0.5.1
        """.trimIndent()
        assertEquals("", Releases.notesForPeople(onlyUpkeep))
        assertEquals("", Releases.notesForPeople(""))
        assertEquals("• New petals\n• A calmer sky", Releases.notesForPeople("New petals\n- a calmer sky"))
    }

    @Test
    fun `nothing is offered when current, older, unparseable, draft or pre-release`() {
        assertNull(Releases.updateFrom(release(), current = "1.2.0"))
        assertNull(Releases.updateFrom(release(), current = "1.3.0"))
        assertNull(Releases.updateFrom(release(tag = "nightly"), current = "1.0.0"))
        assertNull(Releases.updateFrom(release(), current = "dev"))
        assertNull(Releases.updateFrom(release(draft = true), current = "1.0.0"))
        assertNull(Releases.updateFrom(release(prerelease = true), current = "1.0.0"))
    }

    @Test
    fun `releases without an apk are skipped, and meadow apks are preferred`() {
        assertNull(Releases.updateFrom(release(assets = """[{"name":"notes.txt","browser_download_url":"u"}]"""), "1.0.0"))
        val update = Releases.updateFrom(
            release(
                assets = """[{"name":"other.apk","browser_download_url":"https://x/other"},
                    {"name":"Meadow-1.2.0.apk","browser_download_url":"https://x/meadow"}]""",
            ),
            "1.0.0",
        )
        assertEquals("https://x/meadow", update?.apkUrl)
        assertNull(update?.sha256)
    }

    @Test
    fun `sha256 matches a known digest`() {
        val file = java.io.File.createTempFile("meadow", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Updater.sha256(file))
    }

    @Test
    fun `a pre-release reads with its suffix`() {
        assertEquals("1.5.0-beta.1", Version.parse("v1.5.0-beta.1").toString())
        assertEquals("2.0.0", Version.parse("2.0").toString())
    }

    @Test
    fun `notes keep what changed for people, a line each`() {
        val body = """
            ## What's Changed
            **Full Changelog**: https://example.com/compare
            <!-- Release notes generated -->
            * feat(chat): open at what's new by @sam in #12
            * fix: a garden that didn't wake by @ana in #13
            * docs: say how to build by @sam in #14
            * Rest screen: read the usage log once by @ana in #15
            * odd(scope): kept as it reads
            @sam made their first contribution in #12
        """.trimIndent()
        assertEquals(
            listOf("• Open at what's new", "• A garden that didn't wake", "• Rest screen: read the usage log once", "• Odd(scope): kept as it reads").joinToString("\n"),
            Releases.notesForPeople(body),
        )
        assertEquals("", Releases.notesForPeople("## Only a heading"))
    }

    @Test
    fun `notes with a part for people show only that part, as plain lines`() {
        val body = """
            <!-- for people: start -->
            Your garden now waters itself.

            ### New
            - Water the garden from the rest screen (#7)
            ### Fixed
            - A plant grows again after a long night (#8) (#9)
            <!-- for people: end -->

            ## Highlights
            ### Water the garden
            <img src="https://example.com/after/1.png" width="300" alt="Water a plant">

            ## All changes
            - feat(app): water the garden (#7)
        """.trimIndent()
        assertEquals(
            listOf(
                "Your garden now waters itself.",
                "New",
                "• Water the garden from the rest screen",
                "Fixed",
                "• A plant grows again after a long night",
            ),
            Releases.notesForPeople(body).lines(),
        )
        // Without its end line, the part isn't trusted, and the notes read as GitHub's.
        assertEquals("• Water the garden", Releases.notesForPeople("<!-- for people: start -->\n- feat: water the garden"))
    }

    @Test
    fun `Meadow's own APK is picked, and a digest that isn't SHA-256 is no checksum`() {
        val release = GitHubRelease(
            tag = "v9.0.0",
            assets = listOf(
                GitHubAsset("other.apk", "https://example.com/other.apk", 5),
                GitHubAsset("meadow-9.0.0.apk", "https://example.com/meadow.apk", 7, digest = "md5:ABC"),
                GitHubAsset("notes.txt", "https://example.com/notes.txt", 1),
            ),
        )
        val update = Releases.updateFrom(release, "1.0.0")!!
        assertEquals("https://example.com/meadow.apk", update.apkUrl)
        assertEquals(null, update.sha256)
        assertEquals("", update.notes)
        assertEquals(null, Releases.updateFrom(release.copy(assets = listOf(GitHubAsset("notes.txt", "u", 1))), "1.0.0"))
        assertEquals(null, Releases.updateFrom(release.copy(tag = "nightly"), "1.0.0"))
        assertEquals(null, Releases.updateFrom(release, "not a version"))
        assertEquals(null, Releases.updateFrom(release.copy(prerelease = true), "1.0.0"))
    }
}
