package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllCompanionGeneratorsTest {

    companion object {
        @JvmStatic
        fun companions(): List<Array<Any>> = listOf(
            arrayOf("mailhog", MailhogCompanionGenerator()),
            arrayOf("minio",   MinioCompanionGenerator()),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("companions")
    fun `companion name matches its compose service header`(@Suppress("UNUSED_PARAMETER") name: String, gen: CompanionGenerator) {
        val block = gen.composeServiceBlock()
        assertContains(block, "  ${gen.companionName}:",
            message = "companion block must start with its declared name as a service key")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("companions")
    fun `compose block ends with a trailing newline`(@Suppress("UNUSED_PARAMETER") name: String, gen: CompanionGenerator) {
        assertTrue(gen.composeServiceBlock().endsWith("\n"),
            "compose service block must end with a newline so the appender concatenates cleanly")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("companions")
    fun `env overlay keys are non-empty and uppercase`(@Suppress("UNUSED_PARAMETER") name: String, gen: CompanionGenerator) {
        gen.envOverlay().keys.forEach { key ->
            assertTrue(key.isNotEmpty(), "env overlay key must not be empty")
            assertEquals(key.uppercase(), key, "env overlay keys must be uppercase by convention")
        }
    }

    @Test
    fun `mailhog exposes SMTP_HOST and SMTP_PORT`() {
        val env = MailhogCompanionGenerator().envOverlay()
        assertEquals("mailhog", env["SMTP_HOST"])
        assertEquals("1025", env["SMTP_PORT"])
    }

    @Test
    fun `minio exposes S3 endpoint and credentials`() {
        val env = MinioCompanionGenerator().envOverlay()
        assertEquals("http://minio:9000", env["S3_ENDPOINT"])
        assertContains(env.keys, "S3_ACCESS_KEY")
        assertContains(env.keys, "S3_SECRET_KEY")
    }

    @Test
    fun `minio declares minio_data as a named volume`() {
        assertEquals(listOf("minio_data"), MinioCompanionGenerator().namedVolumes())
    }

    @Test
    fun `mailhog declares no named volumes`() {
        assertEquals(emptyList(), MailhogCompanionGenerator().namedVolumes())
    }
}
