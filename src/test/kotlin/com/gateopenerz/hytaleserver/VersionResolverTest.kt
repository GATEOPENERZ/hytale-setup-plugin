package com.gateopenerz.hytaleserver

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VersionResolverTest {

    private val resolver = VersionResolver()

    @Test
    fun `extractXmlTagValue extracts simple tag value`() {
        val xml = "<metadata><release>1.2.3</release></metadata>"
        assertEquals("1.2.3", resolver.extractXmlTagValue(xml, "release"))
    }

    @Test
    fun `extractXmlTagValue returns null for missing tag`() {
        val xml = "<metadata><latest>1.0.0</latest></metadata>"
        assertNull(resolver.extractXmlTagValue(xml, "release"))
    }

    @Test
    fun `extractXmlTagValue trims whitespace`() {
        val xml = "<release>  1.2.3  </release>"
        assertEquals("1.2.3", resolver.extractXmlTagValue(xml, "release"))
    }

    @Test
    fun `extractXmlTagValue is case insensitive`() {
        val xml = "<Release>1.2.3</Release>"
        assertEquals("1.2.3", resolver.extractXmlTagValue(xml, "release"))
    }

    @Test
    fun `extractXmlTagValue returns null for empty tag`() {
        val xml = "<release>  </release>"
        assertNull(resolver.extractXmlTagValue(xml, "release"))
    }

    @Test
    fun `extractLastVersionInVersionsBlock returns last version`() {
        val xml = """
            <versions>
                <version>1.0.0</version>
                <version>1.1.0</version>
                <version>1.2.0</version>
            </versions>
        """.trimIndent()
        assertEquals("1.2.0", resolver.extractLastVersionInVersionsBlock(xml))
    }

    @Test
    fun `extractLastVersionInVersionsBlock returns null for missing block`() {
        val xml = "<metadata><release>1.0.0</release></metadata>"
        assertNull(resolver.extractLastVersionInVersionsBlock(xml))
    }

    @Test
    fun `extractLastVersionInVersionsBlock returns null for empty block`() {
        val xml = "<versions></versions>"
        assertNull(resolver.extractLastVersionInVersionsBlock(xml))
    }

    @Test
    fun `extractLastVersionInVersionsBlock handles single version`() {
        val xml = "<versions><version>2026.01.29-build.123</version></versions>"
        assertEquals("2026.01.29-build.123", resolver.extractLastVersionInVersionsBlock(xml))
    }

    @Test
    fun `extractLastVersionInVersionsBlock trims whitespace in versions`() {
        val xml = """
            <versions>
                <version>  1.0.0  </version>
                <version>  2.0.0  </version>
            </versions>
        """.trimIndent()
        assertEquals("2.0.0", resolver.extractLastVersionInVersionsBlock(xml))
    }

    @Test
    fun `detectVersion returns unknown for missing directory`() {
        val fakeDir = java.io.File("/tmp/nonexistent-" + System.nanoTime())
        assertEquals("unknown", resolver.detectVersion(fakeDir))
    }
}
