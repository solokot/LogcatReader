package com.dp.logcat

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class LogcatStreamReader(inputStream: InputStream) : Iterator<Log>, Closeable {
  private val reader: BufferedReader = BufferedReader(InputStreamReader(inputStream))
  private val msgBuffer = StringBuilder()
  private lateinit var log: Log
  private var id = 0

  override fun hasNext(): Boolean {
    while (true) {
      val metadata = reader.readLine()?.trim() ?: return false
      if (metadata.startsWith("[")) {
        var msg = reader.readLine() ?: return false
        msgBuffer.append(msg)

        msg = reader.readLine() ?: return false
        while (msg.isNotEmpty()) {
          msgBuffer.append("\n")
            .append(msg)

          msg = reader.readLine() ?: return false
        }

        return try {
          log = Log.parse(id = id, metadata = metadata, msg = msgBuffer.toString())
          id += 1
          true
        } catch (_: Exception) {
          // Logger.debug(Logcat::class, "${e.message}: $metadata")
          false
        } finally {
          msgBuffer.setLength(0)
        }
      }
    }
  }

  override fun next() = log

  override fun close() {
    try {
      reader.close()
    } catch (ignored: IOException) {
    }
  }
}