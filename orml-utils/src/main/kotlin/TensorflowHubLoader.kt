package org.openrndr.orml.utils

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.openrndr.platform.Platform
import org.tensorflow.SavedModelBundle
import java.io.BufferedInputStream
import java.io.File
import java.math.BigInteger
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

private fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
}

private fun String.md5Short(): String {
    return md5().take(8)
}

private const val gzipBufferSize = 1024 * 1024

/**
 * This does not work due to issues described in: https://github.com/tensorflow/hub/issues/194
 */
fun loadFromTensorflowHub(url: String): SavedModelBundle {
    val sup = Platform.supportDirectory("orml")
    val hashDir = "bundle-${url.md5Short()}"
    val targetDir = File(sup, hashDir)
    targetDir.mkdirs()

    require(targetDir.exists()) {
        """failed to create target path $targetDir"""
    }

    val bundle = File(targetDir, "bundle.tar.gz")

    if (!bundle.exists()) {
        println("downloading bundle from $url to $bundle")
        val result = URL(url).readBytes()
        bundle.writeBytes(result)

        run {
            val fin = Files.newInputStream(bundle.toPath())
            val `in` = BufferedInputStream(fin)
            val out = Files.newOutputStream(File(targetDir, "bundle.tar").toPath())
            val gzIn = GzipCompressorInputStream(`in`)
            val buffer = ByteArray(gzipBufferSize)
            var n = 0
            while (-1 != gzIn.read(buffer).also { n = it }) {
                out.write(buffer, 0, n)
            }
            out.close()
            gzIn.close()
        }

        run {
            val fin = Files.newInputStream(File(targetDir, "bundle.tar").toPath())

            TarArchiveInputStream(fin).use { fin ->
                var centry: TarArchiveEntry? = null
                while (fin.nextTarEntry?.also { centry = it } != null) {
                    val entry = centry!!
                    if (entry.isDirectory) {
                        continue
                    }
                    val curfile: File = File(targetDir, entry.name)
                    val parent = curfile.parentFile
                    if (!parent.exists()) {
                        parent.mkdirs()
                    }
                    val bytes = fin.readBytes()
                    curfile.writeBytes(bytes)
                }
            }
        }
    }
    val loader = SavedModelBundle.loader(targetDir.absolutePath)
    val res = loader.load()
    return res
}