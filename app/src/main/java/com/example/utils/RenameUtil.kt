package com.example.utils

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import com.example.MainActivity
import java.io.File

object RenameUtil {

    fun shizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun executeShizukuCommand(command: String): Boolean {
        if (!shizukuAvailable()) {
            return false
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun executeShizukuScriptAsync(script: String, onProgress: (String) -> Unit, onComplete: (Boolean) -> Unit) {
        if (!shizukuAvailable()) {
            onProgress("STATUS:Shizuku not available")
            onComplete(false)
            return
        }
        Thread {
            try {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply {
                    isAccessible = true
                }
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", script),
                    null,
                    null
                ) as rikka.shizuku.ShizukuRemoteProcess
                
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                
                val errThread = Thread {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        onProgress(line!!)
                    }
                }
                errThread.start()
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onProgress(line!!)
                }
                
                process.waitFor()
                errThread.join()
                onComplete(process.exitValue() == 0)
            } catch (e: Exception) {
                e.printStackTrace()
                onProgress("STATUS:Error: ${e.message}")
                onComplete(false)
            }
        }.start()
    }

    fun executeShizukuCommandWithOutput(command: String): String {
        if (!shizukuAvailable()) {
            return "Shizuku not available"
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
            while (errReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }


    fun checkDirExists(path: String): Boolean {
        if (!shizukuAvailable()) {
            return File(path).exists()
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", "[ -d \"$path\" ]"),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            process.waitFor() == 0
        } catch (e: Exception) {
            File(path).exists()
        }
    }

    fun checkFileExists(path: String): Boolean {
        if (!shizukuAvailable()) {
            return File(path).exists()
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", "[ -f \"$path\" ]"),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            process.waitFor() == 0
        } catch (e: Exception) {
            File(path).exists()
        }
    }

    fun turnOn(): Boolean {
        val baseDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
        val gDir = "$baseDir/gameassetbundles"
        val gData = "$baseDir/gameassetbundles-data"
        val gMujahi = "$baseDir/gameassetbundles-mujahi"
        val fileinfo = "$baseDir/fileinfo"
        val fileinfoData = "$baseDir/fileinfo-data"
        val fileinfoMujahi = "$baseDir/fileinfo-mujahi"
        
        if (checkDirExists(gMujahi)) {
            val cmd = """
                mv "$gDir" "$gData" && \
                mv "$gMujahi" "$gDir" && \
                mv "$fileinfo" "$fileinfoData" && \
                mv "$fileinfoMujahi" "$fileinfo"
            """.trimIndent()
            return executeShizukuCommand(cmd)
        }
        return false
    }

    fun turnOff(): String {
        val baseDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
        val gDir = "$baseDir/gameassetbundles"
        val gData = "$baseDir/gameassetbundles-data"
        val gMujahi = "$baseDir/gameassetbundles-mujahi"
        val fileinfo = "$baseDir/fileinfo"
        val fileinfoData = "$baseDir/fileinfo-data"
        val fileinfoMujahi = "$baseDir/fileinfo-mujahi"
        
        if (checkDirExists(gData)) {
            val cmd = """
                mv "$gDir" "$gMujahi" && \
                mv "$gData" "$gDir" && \
                mv "$fileinfo" "$fileinfoMujahi" && \
                mv "$fileinfoData" "$fileinfo"
            """.trimIndent()
            val success = executeShizukuCommand(cmd)
            return if (success) "SUCCESS" else "ERROR"
        }
        return "DIR_NOT_FOUND"
    }

    fun copyDirectory(source: File, target: File) {
        val srcPath = source.absolutePath
        val destPath = target.absolutePath
        if (shizukuAvailable()) {
            val cmd = "mkdir -p \"$destPath\" && cp -r \"$srcPath\"/. \"$destPath\""
            executeShizukuCommand(cmd)
        } else {
            // fallback (will likely fail on Android 11+ but keeps compilation and structure)
            if (!target.exists()) target.mkdirs()
            source.listFiles()?.forEach { file ->
                val dest = File(target, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, dest)
                } else {
                    file.copyTo(dest, overwrite = true)
                }
            }
        }
    }

    fun extractZipToDirectoryMerge(zipFile: File, targetDir: File) {
        val zipPath = zipFile.absolutePath
        val destPath = targetDir.absolutePath
        if (shizukuAvailable()) {
            val cmd = "unzip -o \"$zipPath\" -d \"$destPath\""
            executeShizukuCommand(cmd)
        } else {
            // fallback (will likely fail on Android 11+ but fallback exists)
            java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
                var entry: java.util.zip.ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }
}
