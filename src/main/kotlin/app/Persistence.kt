package app

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

private val stateFilePath: Path = Paths.get(
    System.getProperty("user.home"),
    ".runner-sheet-input",
    "state.json",
)

fun appStateLocation(): Path = stateFilePath

fun loadAppState(): AppState = runCatching {
    if (!Files.exists(stateFilePath)) {
        defaultAppState()
    } else {
        json.decodeFromString<AppState>(Files.readString(stateFilePath)).normalized()
    }
}.getOrElse {
    defaultAppState()
}

fun saveAppState(state: AppState) {
    Files.createDirectories(stateFilePath.parent)
    Files.writeString(
        stateFilePath,
        json.encodeToString(state.normalized()),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
    )
}
