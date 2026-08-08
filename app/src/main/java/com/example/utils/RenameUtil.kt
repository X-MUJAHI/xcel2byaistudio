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

    fun turnOn(): Boolean {
        val baseDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
        val gDir = "$baseDir/gameassetbundles"
        val gData = "$baseDir/gameassetbundles-data"
        val gMujahi = "$baseDir/gameassetbundles-mujahi"
        val fileinfo = "$baseDir/fileinfo"
        val fileinfoData = "$baseDir/fileinfo-data"
        val fileinfoMujahi = "$baseDir/fileinfo-mujahi"
        
        if (checkDirExists(gMujahi)) {
            if (useShizukuOps && shizukuAvailable()) {
                val cmd = """
                    mv "$gDir" "$gData" && \
                    mv "$gMujahi" "$gDir" && \
                    mv "$fileinfo" "$fileinfoData" && \
                    mv "$fileinfoMujahi" "$fileinfo"
                """.trimIndent()
                return executeShizukuCommand(cmd)
            } else {
                val f1 = File(gDir).renameTo(File(gData))
                val f2 = File(gMujahi).renameTo(File(gDir))
                val f3 = File(fileinfo).renameTo(File(fileinfoData))
                val f4 = File(fileinfoMujahi).renameTo(File(fileinfo))
                return f1 && f2 && f3 && f4
            }
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
            if (useShizukuOps && shizukuAvailable()) {
                val cmd = """
                    mv "$gDir" "$gMujahi" && \
                    mv "$gData" "$gDir" && \
                    mv "$fileinfo" "$fileinfoMujahi" && \
                    mv "$fileinfoData" "$fileinfo"
                """.trimIndent()
                val success = executeShizukuCommand(cmd)
                return if (success) "SUCCESS" else "ERROR"
            } else {
                val f1 = File(gDir).renameTo(File(gMujahi))
                val f2 = File(gData).renameTo(File(gDir))
                val f3 = File(fileinfo).renameTo(File(fileinfoMujahi))
                val f4 = File(fileinfoData).renameTo(File(fileinfo))
                return if (f1 && f2 && f3 && f4) "SUCCESS" else "ERROR"
            }
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
        val baseDir = File("/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android")
        val gDir = File(baseDir, "gameassetbundles")
        val gData = File(baseDir, "gameassetbundles-data")
        val fileinfo = File(baseDir, "fileinfo")
        val fileinfoData = File(baseDir, "fileinfo-data")

        if (useShizukuOps && shizukuAvailable()) {
            val script = """
                #!/system/bin/sh
                ZIP_FILE="${zipFile.absolutePath}"
                DEST_DIR="${destDir.absolutePath}"
                BASE_DIR="${baseDir.absolutePath}"
                G_DIR="${gDir.absolutePath}"
                G_DATA="${gData.absolutePath}"
                FILEINFO="${fileinfo.absolutePath}"
                FILEINFO_DATA="${fileinfoData.absolutePath}"
                
                if [ ! -f "${'$'}ZIP_FILE" ]; then
                    echo "STATUS:Error: Zip file not found" >&2
                    exit 1
                fi
                
                echo "STATUS:Copying gameassetbundles backup..."
                if [ ! -d "${'$'}G_DATA" ]; then
                    if ! cp -pr "${'$'}G_DIR" "${'$'}G_DATA"; then
                        cp -r "${'$'}G_DIR" "${'$'}G_DATA"
                    fi
                fi
                
                echo "STATUS:Copying fileinfo backup..."
                if [ ! -f "${'$'}FILEINFO_DATA" ]; then
                    if ! cp -p "${'$'}FILEINFO" "${'$'}FILEINFO_DATA"; then
                        cp "${'$'}FILEINFO" "${'$'}FILEINFO_DATA"
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
                    onProgress("STATUS:Copying gameassetbundles backup...")
                    if (!gData.exists()) {
                        copyDirectory(gDir, gData)
                    }
                    onProgress("STATUS:Copying fileinfo backup...")
                    if (!fileinfoData.exists()) {
                        fileinfo.copyTo(fileinfoData, overwrite = true)
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
