package dev.pcoffline.meadow.update

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** A web server for [Updater]: it gives [answer] for each address, and records the addresses asked for. */
class FakeWeb(val answer: (String) -> Answer) {
    val asked = mutableListOf<String>()

    fun open(url: String): HttpURLConnection {
        asked += url
        return FakeConnection(url, answer(url))
    }

    /** An answer with [code] and [body]; [failure] is thrown in place of an answer. */
    class Answer(
        val code: Int,
        val body: ByteArray = ByteArray(0),
        val length: Long = body.size.toLong(),
        val failure: IOException? = null,
        val stream: () -> InputStream = { ByteArrayInputStream(body) },
    )

    private class FakeConnection(url: String, val answer: Answer) : HttpURLConnection(URL(url)) {
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() = Unit
        override fun getResponseCode(): Int = answer.failure?.let { throw it } ?: answer.code
        override fun getContentLengthLong(): Long = answer.length
        override fun getInputStream(): InputStream = answer.stream()
    }
}
