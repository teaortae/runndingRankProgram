package app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.launch

private data class PhotoDraft(
    val imagePath: String = "",
    val recognizedText: String = "",
    val nicknameInput: String = "",
    val distanceInput: String = "",
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "러닝 시트 입력기",
        state = rememberWindowState(width = 1320.dp, height = 880.dp),
    ) {
        MaterialTheme {
            RunnerSheetApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunnerSheetApp() {
    var state by remember { mutableStateOf(loadAppState().normalized()) }
    var syncBusy by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var photoDraft by remember { mutableStateOf(PhotoDraft()) }
    val busy = syncBusy || photoBusy
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun commit(newState: AppState) {
        state = newState.normalized()
    }

    fun openSheet(urlInput: String) {
        val url = urlInput.trim()
        if (url.isBlank()) {
            notify("시트 링크를 먼저 입력하세요.")
            return
        }
        runCatching {
            openUrlInBrowser(url)
        }.onSuccess {
            notify("브라우저에서 시트를 열었습니다.")
        }.onFailure {
            notify(it.message ?: "시트를 열지 못했습니다. 링크를 확인하세요.")
        }
    }

    suspend fun syncState(newState: AppState, successMessage: String): Boolean {
        val sheetUrl = newState.sheetUrl.trim()
        if (sheetUrl.isBlank()) {
            notify("시트 링크를 먼저 저장하세요.")
            return false
        }

        syncBusy = true
        return try {
            val message = syncSheet(sheetUrl, newState.copy(sheetUrl = sheetUrl).toTsv())
            commit(newState.copy(sheetUrl = sheetUrl))
            notify("$successMessage $message")
            true
        } catch (error: Throwable) {
            notify(error.message ?: "시트 반영에 실패했습니다.")
            false
        } finally {
            syncBusy = false
        }
    }

    suspend fun saveRunnerChanges(
        runnerId: String,
        nickname: String,
        gender: Gender,
        goalText: String,
        distanceText: String,
    ): Boolean {
        val trimmedNickname = nickname.trim()
        if (trimmedNickname.isBlank()) {
            notify("참가자 이름을 입력하세요.")
            return false
        }
        val goalKm = parseKilometers(goalText)
        if (goalKm == null) {
            notify("목표 거리를 숫자로 입력하세요.")
            return false
        }
        val distanceKm = parseKilometers(distanceText)
        if (distanceKm == null) {
            notify("누적 거리를 숫자로 입력하세요.")
            return false
        }

        val newState = updateRunner(state, runnerId) { runner ->
            runner.copy(
                nickname = trimmedNickname,
                gender = gender,
                goalKm = goalKm,
                distanceKm = distanceKm,
            )
        }
        return syncState(newState, "$trimmedNickname 정보 저장 완료.")
    }

    suspend fun deleteRunnerAndSync(runnerId: String): Boolean {
        val runner = state.runners.firstOrNull { it.id == runnerId } ?: return false
        val newState = deleteRunner(state, runnerId)
        val synced = syncState(newState, "${runner.nickname} 삭제 완료.")
        if (synced && photoDraft.nicknameInput == runner.nickname) {
            photoDraft = photoDraft.copy(nicknameInput = "")
        }
        return synced
    }

    suspend fun applyRunAndSync(nickname: String, distanceText: String): Boolean {
        val runner = state.runners.firstOrNull { it.nickname == nickname.trim() }
        if (runner == null) {
            val availableNames = state.runners.joinToString(", ") { it.nickname }
            notify("등록된 이름과 일치하지 않습니다. 사용 가능: $availableNames")
            return false
        }
        val distance = parseKilometers(distanceText)
        if (distance == null) {
            notify("거리를 숫자로 입력하세요.")
            return false
        }

        val newState = recordRun(state, runner.id, distance)
        return syncState(
            newState,
            "${runner.nickname} +${formatDistanceKm(distance)} km 반영 완료.",
        )
    }

    fun selectPhoto() {
        if (busy) {
            return
        }
        EventQueue.invokeLater {
            val dialog = FileDialog(null as Frame?, "사진 선택", FileDialog.LOAD)
            dialog.isVisible = true
            val fileName = dialog.file ?: return@invokeLater
            val selected = File(dialog.directory ?: "", fileName)
            scope.launch {
                photoBusy = true
                photoDraft = PhotoDraft(imagePath = selected.absolutePath)
                try {
                    val result = analyzePhoto(selected, state.runners)
                    photoDraft = photoDraft.copy(
                        recognizedText = result.recognizedText,
                        nicknameInput = result.detectedNickname.orEmpty(),
                        distanceInput = result.detectedDistanceKm
                            ?.let(::formatDistanceKm)
                            .orEmpty(),
                    )
                    notify("사진 분석을 완료했습니다. 필요하면 이름과 거리를 수정한 뒤 반영하세요.")
                } catch (error: Throwable) {
                    notify(error.message ?: "사진 분석에 실패했습니다.")
                } finally {
                    photoBusy = false
                }
            }
        }
    }

    LaunchedEffect(state) {
        saveAppState(state)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("러닝 시트 입력기")
                        Text(
                            text = "참가자를 고르고 사진 업로드로 오늘 기록을 반영합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RunnerListCard(
                runners = state.runners,
                selectedRunnerId = state.selectedRunnerId,
                onSelectRunner = { runnerId ->
                    commit(state.copy(selectedRunnerId = runnerId))
                },
                onAddRunner = {
                    commit(addRunner(state))
                    notify("새 참가자를 추가했습니다. 오른쪽에서 정보를 저장하면 시트에 반영됩니다.")
                },
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SheetLinkCard(
                    sheetUrl = state.sheetUrl,
                    onSaveSheetUrl = { url ->
                        commit(state.copy(sheetUrl = url))
                        notify("시트 링크를 저장했습니다.")
                    },
                    onOpenSheet = ::openSheet,
                )

                PhotoUploadCard(
                    draft = photoDraft,
                    busy = busy,
                    photoBusy = photoBusy,
                    onSelectPhoto = ::selectPhoto,
                    onDraftChange = { photoDraft = it },
                    onApply = {
                        scope.launch {
                            applyRunAndSync(photoDraft.nicknameInput, photoDraft.distanceInput)
                        }
                    },
                )

                RunnerEditorCard(
                    runner = state.selectedRunner(),
                    busy = syncBusy,
                    onSave = { runnerId, nickname, gender, goalText, distanceText ->
                        scope.launch {
                            saveRunnerChanges(
                                runnerId = runnerId,
                                nickname = nickname,
                                gender = gender,
                                goalText = goalText,
                                distanceText = distanceText,
                            )
                        }
                    },
                    onDelete = { runnerId ->
                        scope.launch {
                            deleteRunnerAndSync(runnerId)
                        }
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RunnerListCard(
    runners: List<Runner>,
    selectedRunnerId: String?,
    onSelectRunner: (String) -> Unit,
    onAddRunner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("참가자", style = MaterialTheme.typography.titleLarge)
            Text(
                "왼쪽에서 참가자를 고르고, 오른쪽에서 사진 기록이나 기본 정보를 반영합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddRunner,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("새 참가자 추가", fontWeight = FontWeight.SemiBold)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                runners.forEach { runner ->
                    val selected = runner.id == selectedRunnerId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRunner(runner.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(runner.nickname, fontWeight = FontWeight.SemiBold)
                            Text(
                                runner.progressLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLinkCard(
    sheetUrl: String,
    onSaveSheetUrl: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
) {
    var sheetUrlInput by remember(sheetUrl) { mutableStateOf(sheetUrl) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Google Sheets 링크", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = sheetUrlInput,
                onValueChange = { sheetUrlInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("시트 링크") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSaveSheetUrl(sheetUrlInput.trim()) }) {
                    Text("링크 저장")
                }
                Button(onClick = { onOpenSheet(sheetUrlInput) }) {
                    Text("시트 열기")
                }
            }
            Text(
                text = "앱 상태 파일: ${appStateLocation()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunnerEditorCard(
    runner: Runner?,
    busy: Boolean,
    onSave: (runnerId: String, nickname: String, gender: Gender, goalText: String, distanceText: String) -> Unit,
    onDelete: (runnerId: String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (runner == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text("참가자를 먼저 선택하세요.")
            }
            return@Card
        }

        var nicknameInput by remember(runner.id) { mutableStateOf(runner.nickname) }
        var genderInput by remember(runner.id) { mutableStateOf(runner.gender) }
        var goalInput by remember(runner.id) { mutableStateOf(formatGoalKm(runner.goalKm)) }
        var distanceInput by remember(runner.id) { mutableStateOf(formatDistanceKm(runner.distanceKm)) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("선택한 참가자", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "이름, 성별, 목표 거리, 누적 거리를 수정한 뒤 저장하면 시트 전체를 갱신합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp))
                }
            }

            OutlinedTextField(
                value = nicknameInput,
                onValueChange = { nicknameInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("이름") },
                singleLine = true,
                enabled = !busy,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gender.entries.forEach { gender ->
                    if (gender == genderInput) {
                        Button(
                            onClick = { genderInput = gender },
                            enabled = !busy,
                        ) {
                            Text(gender.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { genderInput = gender },
                            enabled = !busy,
                        ) {
                            Text(gender.label)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("목표 거리 (km)") },
                    singleLine = true,
                    enabled = !busy,
                )
                OutlinedTextField(
                    value = distanceInput,
                    onValueChange = { distanceInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("누적 거리 (km)") },
                    singleLine = true,
                    enabled = !busy,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(runner.id, nicknameInput, genderInput, goalInput, distanceInput)
                    },
                    enabled = !busy,
                ) {
                    Text("정보 저장", fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { onDelete(runner.id) },
                    enabled = !busy,
                ) {
                    Text("참가자 삭제")
                }
            }
        }
    }
}

@Composable
private fun PhotoUploadCard(
    draft: PhotoDraft,
    busy: Boolean,
    photoBusy: Boolean,
    onSelectPhoto: () -> Unit,
    onDraftChange: (PhotoDraft) -> Unit,
    onApply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("사진 업로드", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (isMacOs()) {
                            "이름과 거리 정보가 보이는 사진을 올리면 OCR로 찾아서 시트에 바로 반영합니다."
                        } else {
                            "사진 OCR은 현재 macOS에서만 지원됩니다. Windows 빌드에서는 참가자 정보 수정과 시트 동기화만 사용할 수 있습니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (photoBusy) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp))
                }
            }

            Button(
                onClick = onSelectPhoto,
                enabled = !busy && isMacOs(),
            ) {
                Text("사진 선택")
            }

            Text(
                if (draft.imagePath.isBlank()) {
                    "선택한 사진이 없습니다."
                } else {
                    "선택한 사진: ${draft.imagePath}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (draft.recognizedText.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("OCR 텍스트", fontWeight = FontWeight.SemiBold)
                        Text(
                            draft.recognizedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.nicknameInput,
                    onValueChange = { onDraftChange(draft.copy(nicknameInput = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("사진에서 찾은 이름") },
                    singleLine = true,
                    enabled = !busy,
                )
                OutlinedTextField(
                    value = draft.distanceInput,
                    onValueChange = { onDraftChange(draft.copy(distanceInput = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("사진에서 찾은 거리 (km)") },
                    singleLine = true,
                    enabled = !busy,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApply,
                    enabled = !busy &&
                        draft.imagePath.isNotBlank() &&
                        draft.nicknameInput.isNotBlank() &&
                        draft.distanceInput.isNotBlank(),
                ) {
                    Text("사진 기록 반영 후 시트 동기화", fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { onDraftChange(PhotoDraft()) },
                    enabled = !busy,
                ) {
                    Text("입력 지우기")
                }
            }
        }
    }
}

private fun openUrlInBrowser(url: String) {
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        throw IllegalArgumentException("유효한 링크 형식이 아닙니다.")
    }

    val desktopOpened = runCatching {
        if (!Desktop.isDesktopSupported()) {
            return@runCatching false
        }
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return@runCatching false
        }
        desktop.browse(uri)
        true
    }.getOrDefault(false)

    if (desktopOpened) {
        return
    }

    val command = when {
        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> listOf("open", url)
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) ->
            listOf("rundll32", "url.dll,FileProtocolHandler", url)
        else -> listOf("xdg-open", url)
    }

    val process = ProcessBuilder(command).start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        val stderr = process.errorStream.bufferedReader().readText().trim()
        throw IOException(stderr.ifBlank { "브라우저 실행에 실패했습니다." })
    }
}
