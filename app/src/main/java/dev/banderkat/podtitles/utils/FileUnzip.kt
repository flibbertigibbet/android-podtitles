package dev.banderkat.podtitles.utils

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Extension to unzip files. Based on: https://stackoverflow.com/a/50990872
 */
data class FileUnzip(val entry: ZipEntry, val output: File)

fun File.unzip(unzipLocationRoot: File? = null) {
    val rootFolder = unzipLocationRoot ?: File(
        "${parentFile!!.absolutePath}${File.separator}${nameWithoutExtension}"
    )
    if (!rootFolder.exists()) rootFolder.mkdirs()

    ZipFile(this).use { zip ->
        zip
            .entries()
            .asSequence()
            .map {
                val outputFile = File(
                    "${rootFolder.absolutePath}${File.separator}${it.name}"
                )
                FileUnzip(it, outputFile)
            }
            .map {
                it.output.parentFile?.run {
                    if (!exists()) mkdirs()
                }
                it
            }
            .filter { !it.entry.isDirectory }
            .forEach { (entry, output) ->
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
    }
}
