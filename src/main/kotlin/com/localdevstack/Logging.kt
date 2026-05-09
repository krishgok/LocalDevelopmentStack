package com.localdevstack

import java.io.IOException
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

object Logging {

    private const val MAX_BYTES: Long = 10L * 1024L * 1024L
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "localdevstack.log"

    private var initialized = false

    @Synchronized
    fun init() {
        if (initialized) return
        initialized = true

        val logDir = Path.of(LOG_DIR_NAME).toAbsolutePath()
        val logFile = logDir.resolve(LOG_FILE_NAME)

        val root = Logger.getLogger("")
        root.handlers.forEach { root.removeHandler(it) }
        root.level = Level.INFO

        try {
            val handler = SizeCappedFileHandler(logFile, MAX_BYTES)
            handler.level = Level.ALL
            root.addHandler(handler)
        } catch (e: IOException) {
            System.err.println("Warning: could not initialize file logging at $logFile: ${e.message}")
        }
    }

    fun named(name: String): Logger = Logger.getLogger(name)
}

internal class SizeCappedFileHandler(
    private val path: Path,
    private val maxBytes: Long
) : Handler() {

    private var writer: Writer? = null

    init {
        formatter = LineFormatter()
        Files.createDirectories(path.parent)
        openAppending()
    }

    @Synchronized
    private fun openAppending() {
        writer?.runCatching { close() }
        writer = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND
        )
    }

    @Synchronized
    private fun openTruncating() {
        writer?.runCatching { close() }
        writer = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    @Synchronized
    override fun publish(record: LogRecord) {
        if (!isLoggable(record)) return
        val w = writer ?: return
        try {
            if (Files.size(path) >= maxBytes) openTruncating()
            (writer ?: w).write(formatter.format(record))
            (writer ?: w).flush()
        } catch (_: Exception) {
            // Logging must never break the application.
        }
    }

    @Synchronized
    override fun flush() {
        writer?.runCatching { flush() }
    }

    @Synchronized
    override fun close() {
        writer?.runCatching { close() }
        writer = null
    }
}

private class LineFormatter : Formatter() {
    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    override fun format(record: LogRecord): String {
        val sb = StringBuilder(160)
        sb.append(ts.format(Date(record.millis)))
            .append(' ').append(record.level.name)
            .append(' ').append(shortLogger(record.loggerName))
            .append(" - ").append(formatMessage(record))
            .append(System.lineSeparator())
        record.thrown?.let {
            val sw = java.io.StringWriter()
            it.printStackTrace(java.io.PrintWriter(sw))
            sb.append(sw.toString())
        }
        return sb.toString()
    }

    private fun shortLogger(name: String?): String {
        if (name.isNullOrEmpty()) return "-"
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot + 1) else name
    }
}
