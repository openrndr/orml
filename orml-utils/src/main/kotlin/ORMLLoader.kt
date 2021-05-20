package org.openrndr.orml.utils

import org.openrndr.platform.Platform
import java.io.File
import java.security.MessageDigest

private fun bytesToHex(hash: ByteArray): String {
    val hexString = StringBuilder(2 * hash.size)
    for (i in hash.indices) {
        val hex = Integer.toHexString(0xff and hash[i].toInt())
        if (hex.length == 1) {
            hexString.append('0')
        }
        hexString.append(hex)
    }
    return hexString.toString()
}

private val mlmodelsUrl = "https://mlmodels.openrndr.org"

fun fetchORMLModel(modelName: String, modelHash: String): ByteArray {
    val supportDirectory = Platform.supportDirectory("orml")
    val candidate = File(supportDirectory, "$modelName.pb")
    if (!candidate.exists()) {
        println("model not found in cache, downloading $modelName")
        downloadToFile("$mlmodelsUrl/$modelName.pb", candidate)
    } else {
        println("model found in cache: ${candidate.absolutePath}")
    }
    require(candidate.exists())

    val modelBytes = candidate.readBytes()
    val shaDigest = MessageDigest.getInstance("SHA-256")
    val hash = shaDigest.digest(modelBytes)
    val foundHash = bytesToHex(hash)
    require(modelHash == foundHash) {
        """hash for $modelName is $foundHash but expected $modelHash"""
    }
    return modelBytes
}