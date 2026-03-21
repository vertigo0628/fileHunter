package com.vertigo.opposite

import java.io.File
import java.io.FileInputStream

object HexChecker {
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    /**
     * Checks if a file is actually a JPEG picture in disguise.
     * It does this by reading the first 3 "magic bytes" of the file.
     * All JPEGs start with FF D8 FF.
     *
     * When [useShizuku] is true, reads the first 3 bytes by running `head -c 3`
     * via Shizuku's shell (accessed through [ShizukuHelper.executeShizukuCommand]).
     * Falls back to normal file I/O otherwise.
     */
    fun isJpeg(path: String, useShizuku: Boolean = false): Boolean {
        return if (useShizuku) isJpegViaShizuku(path) else isJpegNormal(path)
    }

    private fun isJpegNormal(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return false

            // We only need the first 3 bytes to know if it's a JPEG
            val buffer = ByteArray(3)
            FileInputStream(file).use { stream ->
                val bytesRead = stream.read(buffer, 0, 3)
                bytesRead >= 3 && matchesMagic(buffer)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isJpegViaShizuku(path: String): Boolean {
        return try {
            // We use hexdump via Shizuku because reading the raw bytes over the binder can be messy.
            // This command asks hexdump to just print the first 3 bytes as uppercase hex.
            val command = "hexdump -n 3 -e '3/1 \"%02X\"' \"$path\" 2>/dev/null"
            val output = ShizukuHelper.executeShizukuCommand(command).trim()

            // Does it match the JPEG signature?
            output.uppercase() == "FFD8FF"
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun matchesMagic(buffer: ByteArray): Boolean {
        return buffer[0] == JPEG_MAGIC[0] &&
               buffer[1] == JPEG_MAGIC[1] &&
               buffer[2] == JPEG_MAGIC[2]
    }
}
