package com.example.utils

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import com.example.MainActivity
import java.io.File

object RenameUtil {
    var useShizukuOps: Boolean = true
    var lastError: String = ""

    fun shizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun executeShizukuCommand(command: String): Boolean {
        if (!shizukuAvailable()) {
            lastError = "Shizuku not available or permission denied"
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

            val errBuilder = StringBuilder()
            val errThread = Thread {
                try {
                    val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        errBuilder.append(line).append("\n")
                    }
                } catch (ignored: Exception) {}
            }
            errThread.start()

            val exitCode = process.waitFor()
            errThread.join(2000)

            if (exitCode != 0) {
                lastError = errBuilder.toString().trim()
                if (lastError.isEmpty()) {
                    lastError = "Command exited with code $exitCode"
                }
                android.util.Log.e("RenameUtil", "Command failed ($exitCode): $command\nError: $lastError")
                false
            } else {
                lastError = ""
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = e.message ?: "Unknown exception"
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
            
            val output = StringBuilder()
            val errOutput = StringBuilder()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))

            val errThread = Thread {
                try {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        errOutput.append(line).append("\n")
                    }
                } catch (ignored: Exception) {}
            }
            errThread.start()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor()
            errThread.join(2000)
            if (output.isNotEmpty()) output.toString() else errOutput.toString()
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
                    OPT="$optionalDir"
                    ADIR="${'$'}OPT/android"
                    ADATA="${'$'}OPT/android-data"
                    AMUJAHI="${'$'}OPT/android-mujahi"

                    if [ -d "${'$'}AMUJAHI/android" ] && [ ! -d "${'$'}ADIR" ]; then
                        mv "${'$'}AMUJAHI/android" "${'$'}ADIR"
                    fi

                    if [ -d "${'$'}ADIR" ]; then
                        if [ -d "${'$'}ADATA" ]; then
                            rm -rf "${'$'}OPT/.old_adata" 2>/dev/null
                            mv "${'$'}ADATA" "${'$'}OPT/.old_adata" 2>/dev/null
                        fi
                        mv "${'$'}ADIR" "${'$'}ADATA" || exit 1
                        rm -rf "${'$'}OPT/.old_adata" 2>/dev/null
                    fi

                    if [ -d "${'$'}AMUJAHI" ]; then
                        mv "${'$'}AMUJAHI" "${'$'}ADIR" || exit 1
                    else
                        echo "android-mujahi does not exist" >&2
                        exit 1
                    fi
                    exit 0
                """.trimIndent()
                return executeShizukuCommand(cmd)
            } else {
                val f1 = if (File(aDir).exists()) {
                    if (File(aData).exists()) {
                        File(aData).deleteRecursively()
                    }
                    File(aDir).renameTo(File(aData))
                } else true
                val f2 = File(aMujahi).renameTo(File(aDir))
                return f1 && f2
            }
        }
        lastError = "Folder 'android-mujahi' not found. Please activate the panel first."
        return false
    }

    fun turnOff(): String {
        val optionalDir = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional"
        val aDir = "$optionalDir/android"
        val aData = "$optionalDir/android-data"
        val aMujahi = "$optionalDir/android-mujahi"
        
        if (checkDirExists(aData)) {
            if (useShizukuOps && shizukuAvailable()) {
                val cmd = """
                    OPT="$optionalDir"
                    ADIR="${'$'}OPT/android"
                    ADATA="${'$'}OPT/android-data"
                    AMUJAHI="${'$'}OPT/android-mujahi"

                    if [ -d "${'$'}ADATA/android" ] && [ ! -d "${'$'}ADIR" ]; then
                        mv "${'$'}ADATA/android" "${'$'}ADIR"
                    fi

                    if [ -d "${'$'}ADIR" ]; then
                        if [ -d "${'$'}AMUJAHI" ]; then
                            rm -rf "${'$'}OPT/.old_amujahi" 2>/dev/null
                            mv "${'$'}AMUJAHI" "${'$'}OPT/.old_amujahi" 2>/dev/null
                        fi
                        mv "${'$'}ADIR" "${'$'}AMUJAHI" || exit 1
                        rm -rf "${'$'}OPT/.old_amujahi" 2>/dev/null
                    fi

                    if [ -d "${'$'}ADATA" ]; then
                        mv "${'$'}ADATA" "${'$'}ADIR" || exit 1
                    else
                        echo "android-data does not exist" >&2
                        exit 1
                    fi
                    exit 0
                """.trimIndent()
                val success = executeShizukuCommand(cmd)
                return if (success) "SUCCESS" else "ERROR"
            } else {
                val f1 = if (File(aDir).exists()) {
                    if (File(aMujahi).exists()) {
                        File(aMujahi).deleteRecursively()
                    }
                    File(aDir).renameTo(File(aMujahi))
                } else true
                val f2 = File(aData).renameTo(File(aDir))
                return if (f1 && f2) "SUCCESS" else "ERROR"
            }
        } else if (checkDirExists(aMujahi)) {
            return "SUCCESS"
        }
        lastError = "Neither android-data nor android-mujahi was found."
        return "DIR_NOT_FOUND"
    }

    fun copyDirectory(source: File, target: File): Boolean {
        val srcPath = source.absolutePath
        val destPath = target.absolutePath
        if (useShizukuOps && shizukuAvailable()) {
            val cmd = """
                rm -rf "$destPath" 2>/dev/null
                mkdir -p "$destPath"
                if ! cp -pr "$srcPath/." "$destPath/"; then
                    cp -r "$srcPath/." "$destPath/"
                fi
            """.trimIndent()
            return executeShizukuCommand(cmd)
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
            return target.exists()
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
