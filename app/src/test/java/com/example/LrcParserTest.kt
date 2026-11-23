package com.example

import com.example.data.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parse_validLrcLines_returnsSortedTimestampsAndText() {
        val raw = """
            [ti:Desi Track]
            [00:12.50]Second line
            [00:05.00]First line
            [01:02.125]Third line
        """.trimIndent()

        val result = LrcParser.parse(raw)
        assertEquals(3, result.size)
        assertEquals(5_000L, result[0].timestampMs)
        assertEquals("First line", result[0].text)
        assertEquals(12_500L, result[1].timestampMs)
        assertEquals("Second line", result[1].text)
        assertEquals(62_125L, result[2].timestampMs)
        assertEquals("Third line", result[2].text)
    }

    @Test
    fun parse_blankLrc_returnsEmptyList() {
        assertEquals(emptyList<Any>(), LrcParser.parse(""))
    }
}
