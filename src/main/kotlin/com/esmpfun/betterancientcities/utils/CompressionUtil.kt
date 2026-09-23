package com.esmpfun.betterancientcities.utils

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Gzip helpers for snapshot files. */
object CompressionUtil {

    fun compress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    fun decompress(compressed: ByteArray): ByteArray =
        GZIPInputStream(compressed.inputStream()).use { it.readBytes() }

    /** A readable size such as "1.50 MB". */
    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format(Locale.ROOT, "%.2f MB", mb)
            kb >= 1 -> String.format(Locale.ROOT, "%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }
}
