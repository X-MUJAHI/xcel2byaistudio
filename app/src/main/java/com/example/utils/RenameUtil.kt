package com.example.utils

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import com.example.MainActivity
import java.io.File

object RenameUtil {
    var useShizukuOps: Boolean = true

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
        if (!useShizukuOps || !shizukuAvailable()) {
            return File(path).isDirectory
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
        if (!useShizukuOps || !shizukuAvailable()) {
            return File(path).isFile
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

    fun cleanupLegacyFiles() {
        val optionalDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional"
        val legacyBase = "$optionalDir/android"
        val legacyGData = "$legacyBase/gameassetbundles-data"
        val legacyGMujahi = "$legacyBase/gameassetbundles-mujahi"
        val legacyGDir = "$legacyBase/gameassetbundles"
        val legacyFIData = "$legacyBase/fileinfo-data"
        val legacyFIMujahi = "$legacyBase/fileinfo-mujahi"
        val legacyFIDir = "$legacyBase/fileinfo"

        if (checkDirExists(legacyGData)) {
            if (useShizukuOps && shizukuAvailable()) {
                executeShizukuCommand("""
                    mv "$legacyGDir" "$legacyGMujahi" 2>/dev/null
                    mv "$legacyGData" "$legacyGDir" 2>/dev/null
                    mv "$legacyFIDir" "$legacyFIMujahi" 2>/dev/null
                    mv "$legacyFIData" "$legacyFIDir" 2>/dev/null
                    rm -rf "$legacyGMujahi" "$legacyFIMujahi" 2>/dev/null
                """.trimIndent())
            } else {
                File(legacyGDir).renameTo(File(legacyGMujahi))
                File(legacyGData).renameTo(File(legacyGDir))
                File(legacyFIDir).renameTo(File(legacyFIMujahi))
                File(legacyFIData).renameTo(File(legacyFIDir))
                File(legacyGMujahi).deleteRecursively()
                File(legacyFIMujahi).deleteRecursively()
            }
        } else if (checkDirExists(legacyGMujahi)) {
            if (useShizukuOps && shizukuAvailable()) {
                executeShizukuCommand("rm -rf \"$legacyGMujahi\" \"$legacyFIMujahi\" 2>/dev/null")
            } else {
                File(legacyGMujahi).deleteRecursively()
                File(legacyFIMujahi).deleteRecursively()
            }
        }
    }

    fun turnOn(): Boolean {
        cleanupLegacyFiles()
        val optionalDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional"
        val aDir = "$optionalDir/android"
        val aData = "$optionalDir/android-data"
        val aMujahi = "$optionalDir/android-mujahi"

        if (checkDirExists(aData) && !checkDirExists(aMujahi)) {
            return true
        }
        
        if (checkDirExists(aMujahi)) {
            if (useShizukuOps && shizukuAvailable()) {
                val cmd = """
                    rm -rf "$aData" 2>/dev/null
                    mv "$aDir" "$aData" && \
                    mv "$aMujahi" "$aDir"
                """.trimIndent()
                return executeShizukuCommand(cmd)
            } else {
                if (File(aData).exists()) {
                    File(aData).deleteRecursively()
                }
                val f1 = File(aDir).renameTo(File(aData))
                val f2 = File(aMujahi).renameTo(File(aDir))
                return f1 && f2
            }
        }
        return false
    }

    fun turnOff(): String {
        cleanupLegacyFiles()
        val optionalDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional"
        val aDir = "$optionalDir/android"
        val aData = "$optionalDir/android-data"
        val aMujahi = "$optionalDir/android-mujahi"
        
        if (checkDirExists(aData)) {
            if (useShizukuOps && shizukuAvailable()) {
                val cmd = """
                    rm -rf "$aMujahi" 2>/dev/null
                    mv "$aDir" "$aMujahi" && \
                    mv "$aData" "$aDir"
                """.trimIndent()
                val success = executeShizukuCommand(cmd)
                return if (success) "SUCCESS" else "ERROR"
            } else {
                if (File(aMujahi).exists()) {
                    File(aMujahi).deleteRecursively()
                }
                val f1 = File(aDir).renameTo(File(aMujahi))
                val f2 = File(aData).renameTo(File(aDir))
                return if (f1 && f2) "SUCCESS" else "ERROR"
            }
        } else if (checkDirExists(aMujahi)) {
            return "SUCCESS"
        }
        return "DIR_NOT_FOUND"
    }

    fun copyDirectory(source: File, target: File) {
        val srcPath = source.absolutePath
        val destPath = target.absolutePath
        if (useShizukuOps && shizukuAvailable()) {
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
        if (useShizukuOps && shizukuAvailable()) {
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

    fun getGameUIDs(): List<String> {
        val uids = mutableListOf<String>()
        val dirs = listOf(
            "/storage/emulated/0/Android/data/com.dts.freefiremax/files/Workshop",
            "/storage/emulated/0/Android/data/com.dts.freefireth/files/Workshop"
        )
        if (useShizukuOps && shizukuAvailable()) {
            for (dir in dirs) {
                val output = executeShizukuCommandWithOutput("ls '$dir' 2>/dev/null")
                val lines = output.split("\n")
                for (line in lines) {
                    val name = line.trim()
                    if (name.isNotEmpty() && name.all { it.isDigit() }) {
                        uids.add(name)
                    }
                }
            }
        } else {
            for (dir in dirs) {
                File(dir).listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.all { it.isDigit() }) {
                        uids.add(file.name)
                    }
                }
            }
        }
        return uids.distinct()
    }

    fun installNewScript(zipFile: File, onProgress: (String) -> Unit, onComplete: (Boolean) -> Unit) {
        val destDir = File("/storage/emulated/0/Android/data")
        val optionalDir = File("/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional")
        val aDir = File(optionalDir, "android")
        val aData = File(optionalDir, "android-data")

        if (useShizukuOps && shizukuAvailable()) {
            val script = """
                #!/system/bin/sh
                ZIP_FILE="${zipFile.absolutePath}"
                DEST_DIR="${destDir.absolutePath}"
                OPTIONAL_DIR="${optionalDir.absolutePath}"
                A_DIR="${aDir.absolutePath}"
                A_DATA="${aData.absolutePath}"
                
                if [ ! -f "${'$'}ZIP_FILE" ]; then
                    echo "STATUS:Error: Zip file not found" >&2
                    exit 1
                fi
                
                echo "STATUS:Copying android backup..."
                if [ ! -d "${'$'}A_DATA" ]; then
                    if ! cp -pr "${'$'}A_DIR" "${'$'}A_DATA"; then
                        mkdir -p "${'$'}A_DATA" && cp -r "${'$'}A_DIR/." "${'$'}A_DATA/"
                    fi
                fi
                
                echo "STATUS:Extracting ${zipFile.name}..."
                if unzip -o -q "${'$'}ZIP_FILE" -d "${'$'}DEST_DIR" ; then
                    mv "${'$'}ZIP_FILE" "/storage/emulated/0/Download/xcel1-used-delete-it.zip"
                    echo "STATUS:Done!"
                else
                    echo "STATUS:Error: Unzip failed"
                    exit 1
                fi
            """.trimIndent()
            executeShizukuScriptAsync(script, onProgress, onComplete)
        } else {
            Thread {
                try {
                    if (!zipFile.exists()) {
                        onProgress("STATUS:Error: Zip file not found")
                        onComplete(false)
                        return@Thread
                    }
                    onProgress("STATUS:Copying android backup...")
                    if (!aData.exists()) {
                        copyDirectory(aDir, aData)
                    }
                    onProgress("STATUS:Extracting ${zipFile.name}...")
                    extractZipToDirectoryMerge(zipFile, destDir)
                    zipFile.renameTo(File("/storage/emulated/0/Download/xcel1-used-delete-it.zip"))
                    onProgress("STATUS:Done!")
                    onComplete(true)
                } catch (e: Exception) {
                    onProgress("STATUS:Error: ${e.message}")
                    onComplete(false)
                }
            }.start()
        }
    }
}
