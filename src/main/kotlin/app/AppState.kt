package app

import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID

const val DEFAULT_SHEET_URL =
    "https://docs.google.com/spreadsheets/d/1uTg74WZq__fw0gaJQCH4QLo8NiaqLo90FVG8fZQtbw8/edit?usp=sharing"

private val decimalSymbols = DecimalFormatSymbols(Locale.US)
private val goalFormatter = DecimalFormat("0.##", decimalSymbols)
private val distanceFormatter = DecimalFormat("0.00", decimalSymbols)
private val percentFormatter = DecimalFormat("0.0#", decimalSymbols)

@Serializable
enum class Gender(val label: String) {
    MALE("남"),
    FEMALE("여"),
}

@Serializable
data class Runner(
    val id: String,
    val nickname: String,
    val gender: Gender,
    val goalKm: Double,
    val distanceKm: Double,
)

@Serializable
data class AppState(
    val sheetUrl: String = DEFAULT_SHEET_URL,
    val selectedRunnerId: String? = null,
    val runners: List<Runner> = emptyList(),
)

fun defaultAppState(): AppState {
    val runners = listOf(
        runner("총총", Gender.MALE, 300.0, 51.87),
        runner("동환", Gender.MALE, 61.0, 8.31),
        runner("경훈", Gender.MALE, 50.0, 6.10),
        runner("주이", Gender.FEMALE, 40.0, 13.31),
        runner("은초", Gender.FEMALE, 50.0, 3.65),
        runner("은해", Gender.FEMALE, 50.0, 5.01),
        runner("투어", Gender.MALE, 50.0, 14.11),
        runner("제이", Gender.MALE, 50.0, 5.03),
        runner("신혜", Gender.FEMALE, 40.0, 5.02),
        runner("꾸러기", Gender.FEMALE, 60.0, 0.00),
        runner("두희", Gender.MALE, 50.0, 5.18),
    )
    return AppState(
        sheetUrl = DEFAULT_SHEET_URL,
        selectedRunnerId = runners.firstOrNull()?.id,
        runners = runners,
    )
}

fun AppState.normalized(): AppState {
    if (runners.isEmpty()) {
        return copy(selectedRunnerId = null)
    }
    val validSelection = runners.any { it.id == selectedRunnerId }
    return if (validSelection) this else copy(selectedRunnerId = runners.first().id)
}

fun AppState.selectedRunner(): Runner? = runners.firstOrNull { it.id == selectedRunnerId }

fun Runner.achievementPercent(): Double {
    if (goalKm <= 0.0) return 0.0
    return (distanceKm / goalKm) * 100.0
}

fun Runner.progressLabel(): String =
    "${gender.label} · ${formatDistanceKm(distanceKm)} / ${formatGoalKm(goalKm)} km (${formatPercent(achievementPercent())}%)"

fun Runner.toTsvRow(): String = listOf(
    nickname,
    gender.label,
    "${formatGoalKm(goalKm)} km",
    "${formatDistanceKm(distanceKm)} km",
    "${formatPercent(achievementPercent())}%",
).joinToString("\t")

fun AppState.toTsv(): String = buildString {
    appendLine("닉네임\t성별\t목표 거리\t달린 거리\t달성률")
    runners.forEachIndexed { index, runner ->
        append(runner.toTsvRow())
        if (index != runners.lastIndex) {
            appendLine()
        }
    }
}

fun addRunner(state: AppState): AppState {
    val runner = runner("새 참가자", Gender.MALE, 50.0, 0.0)
    return state.copy(
        runners = state.runners + runner,
        selectedRunnerId = runner.id,
    ).normalized()
}

fun deleteRunner(state: AppState, runnerId: String): AppState = state.copy(
    runners = state.runners.filterNot { it.id == runnerId },
).normalized()

fun updateRunner(
    state: AppState,
    runnerId: String,
    transform: (Runner) -> Runner,
): AppState = state.copy(
    runners = state.runners.map { runner ->
        if (runner.id == runnerId) transform(runner) else runner
    },
    selectedRunnerId = runnerId,
).normalized()

fun recordRun(state: AppState, runnerId: String, distanceKm: Double): AppState {
    val updatedState = updateRunner(state, runnerId) { runner ->
        runner.copy(distanceKm = runner.distanceKm + distanceKm)
    }
    return updatedState.copy(selectedRunnerId = runnerId)
}

fun parseKilometers(input: String): Double? = input
    .trim()
    .removeSuffix("km")
    .removeSuffix("KM")
    .trim()
    .replace(",", ".")
    .toDoubleOrNull()
    ?.takeIf { it >= 0.0 }

fun formatGoalKm(value: Double): String = goalFormatter.format(value)

fun formatDistanceKm(value: Double): String = distanceFormatter.format(value)

fun formatPercent(value: Double): String = percentFormatter.format(value)

private fun runner(
    nickname: String,
    gender: Gender,
    goalKm: Double,
    distanceKm: Double,
): Runner = Runner(
    id = UUID.randomUUID().toString(),
    nickname = nickname,
    gender = gender,
    goalKm = goalKm,
    distanceKm = distanceKm,
)
