package org.openrndr.orml.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.BufferedInputStream

fun downloadFile(url: String): ByteArray {
    val client = OkHttpClient();
    val request: Request = Builder()
        .url(url)
        .build()
    val data = client.newCall(request).execute().use {
        it.body?.bytes() ?: error("no data")
    }
    return data
}

fun downloadToFile(url: String, target: File) {
    val client = OkHttpClient();
    val request: Request = Builder()
        .url(url)
        .build()
    client.newCall(request).execute().use {
        val stream = it.body?.byteStream() ?: error("no stream")
        val contentLength = it.body?.contentLength()
        println("downloading $contentLength bytes")

        val input = BufferedInputStream(stream)
        val output: OutputStream = FileOutputStream(target)
        val data = ByteArray(1024*1024)

        var total: Long = 0
        var count = 0
        while (input.read(data).also { count = it } != -1) {
            total += count
            output.write(data, 0, count)
            println("$total / $contentLength\r")
        }
        output.flush()
        output.close()
        input.close()
    }
}