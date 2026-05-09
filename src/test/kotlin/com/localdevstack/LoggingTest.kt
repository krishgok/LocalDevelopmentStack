package com.localdevstack

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.test.assertTrue

class LoggingTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `file is truncated when size cap is reached`() {
        val maxBytes = 1024L
        val logFile = tempDir.resolve("test.log")
        val handler = SizeCappedFileHandler(logFile, maxBytes)
        try {
            // Each record (with formatter overhead) is well under maxBytes,
            // but enough records will push the file past maxBytes and trigger truncation.
            repeat(200) { i ->
                val record = LogRecord(Level.INFO, "record number $i with some payload to consume bytes")
                record.loggerName = "TestLogger"
                handler.publish(record)
            }
            handler.flush()
        } finally {
            handler.close()
        }

        val finalSize = Files.size(logFile)
        // After truncation, the file size must be below maxBytes plus a single record's worth.
        assertTrue(
            finalSize < maxBytes + 256,
            "expected file to be truncated; size=$finalSize maxBytes=$maxBytes"
        )
    }

    @Test
    fun `handler creates parent directory if missing`() {
        val nested = tempDir.resolve("nested/subdir/test.log")
        val handler = SizeCappedFileHandler(nested, 10_000)
        try {
            val record = LogRecord(Level.INFO, "hello")
            record.loggerName = "TestLogger"
            handler.publish(record)
            handler.flush()
        } finally {
            handler.close()
        }
        assertTrue(Files.exists(nested), "log file should exist at nested path")
    }
}
