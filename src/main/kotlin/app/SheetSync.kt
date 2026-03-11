package app

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val bridgeJson = Json { ignoreUnknownKeys = true }
private val supportDir: Path = Paths.get(
    System.getProperty("user.home"),
    ".runner-sheet-input",
    "support",
)

fun isMacOs(): Boolean = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

@Serializable
private data class OcrOutput(
    val text: String = "",
    val lines: List<String> = emptyList(),
)

data class DetectionResult(
    val recognizedText: String,
    val detectedNickname: String?,
    val detectedDistanceKm: Double?,
)

@Serializable
private data class ScriptResult(
    val status: String,
    val message: String,
    @SerialName("sheet_url")
    val sheetUrl: String? = null,
)

suspend fun analyzePhoto(imageFile: File, runners: List<Runner>): DetectionResult = withContext(Dispatchers.IO) {
    if (!isMacOs()) {
        error("사진 OCR은 현재 macOS에서만 지원됩니다.")
    }
    val swiftScript = ensureSupportFile("ocr_image.swift")
    val output = runCommand(
        command = listOf("xcrun", "swift", swiftScript.toString(), imageFile.absolutePath),
    )
    if (output.exitCode != 0) {
        error(output.errorText.ifBlank { output.stdout }.ifBlank { "이미지 OCR 분석에 실패했습니다." })
    }
    val parsed = parseJsonOutput<OcrOutput>(output, "이미지 OCR 분석 결과를 읽지 못했습니다.")
    DetectionResult(
        recognizedText = parsed.text,
        detectedNickname = detectRunnerNickname(parsed.text, runners),
        detectedDistanceKm = detectDistanceKm(parsed.text),
    )
}

suspend fun syncSheet(sheetUrl: String, tsv: String): String = withContext(Dispatchers.IO) {
    val pythonScript = ensureSupportFile("sync_sheet.py")
    val output = runCommand(
        command = buildPythonCommand(pythonScript, sheetUrl),
        stdinText = tsv,
    )
    if (output.exitCode != 0) {
        error(output.errorText.ifBlank { output.stdout }.ifBlank { "시트 동기화에 실패했습니다." })
    }
    val parsed = parseJsonOutput<ScriptResult>(output, "시트 동기화 결과를 읽지 못했습니다.")
    if (parsed.status != "ok") {
        error(parsed.message)
    }
    parsed.message
}

private fun buildPythonCommand(script: Path, sheetUrl: String): List<String> {
    return if (isWindows()) {
        listOf("py", "-3", script.toString(), sheetUrl)
    } else {
        listOf("python3", script.toString(), sheetUrl)
    }
}

fun detectRunnerNickname(text: String, runners: List<Runner>): String? {
    val normalizedText = text.lowercase().replace("\\s+".toRegex(), "")
    return runners
        .sortedByDescending { it.nickname.length }
        .firstOrNull { runner ->
            val nickname = runner.nickname.lowercase().replace("\\s+".toRegex(), "")
            normalizedText.contains(nickname) || text.contains(runner.nickname, ignoreCase = true)
        }
        ?.nickname
}

fun detectDistanceKm(text: String): Double? {
    val regex = Regex("""(\d+(?:[.,]\d+)?)\s*(km|KM|Km|키로|킬로|킬로미터)""")
    return regex.findAll(text)
        .mapNotNull { match ->
            match.groupValues.getOrNull(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()
        }
        .firstOrNull()
}

private fun ensureSupportFile(resourceName: String): Path {
    Files.createDirectories(supportDir)
    val target = supportDir.resolve(resourceName)
    val resourcePath = "scripts/$resourceName"
    val resourceBytes = checkNotNull(
        Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath),
    ) {
        "지원 스크립트를 찾을 수 없습니다: $resourcePath"
    }.use { it.readBytes() }

    val shouldWrite = !Files.exists(target) || !Files.readAllBytes(target).contentEquals(resourceBytes)
    if (shouldWrite) {
        Files.write(target, resourceBytes)
        target.toFile().setExecutable(true)
    }
    return target
}

private data class CommandOutput(
    val exitCode: Int,
    val stdout: String,
    val errorText: String,
)

private fun runCommand(
    command: List<String>,
    stdinText: String? = null,
): CommandOutput {
    val process = ProcessBuilder(command)
        .start()

    if (stdinText != null) {
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(stdinText)
        }
    } else {
        process.outputStream.close()
    }

    var stdout = ""
    var stderr = ""
    val stdoutReader = thread(name = "runner-sheet-input-stdout") {
        stdout = process.inputStream.bufferedReader().readText().trim()
    }
    val stderrReader = thread(name = "runner-sheet-input-stderr") {
        stderr = process.errorStream.bufferedReader().readText().trim()
    }
    val exitCode = process.waitFor()
    stdoutReader.join()
    stderrReader.join()
    return CommandOutput(
        exitCode = exitCode,
        stdout = stdout,
        errorText = stderr,
    )
}

private inline fun <reified T> parseJsonOutput(
    output: CommandOutput,
    fallbackMessage: String,
): T = try {
    bridgeJson.decodeFromString<T>(output.stdout)
} catch (_: SerializationException) {
    val details = buildString {
        if (output.stdout.isNotBlank()) {
            append(output.stdout)
        }
        if (output.errorText.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(output.errorText)
        }
    }.ifBlank { fallbackMessage }
    error(details)
}
