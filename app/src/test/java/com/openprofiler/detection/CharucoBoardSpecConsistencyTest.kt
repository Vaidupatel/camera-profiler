package com.openprofiler.detection

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Guards against silent drift between the runtime detector config
 * ([charuco_board.json][app/src/main/assets/charuco_board.json]) and the printable
 * target generator spec
 * ([specification.json][app/src/assets/calibration_targets/charuco/specification.json]).
 *
 * These must describe the same physical ChArUco board.
 */
class CharucoBoardSpecConsistencyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun runtimeDetectorConfig_matchesPrintableTargetSpecification() {
        val runtimeFile = resolveRepoFile("src/main/assets/charuco_board.json")
        val printableFile = resolveRepoFile(
            "src/assets/calibration_targets/charuco/specification.json"
        )

        assertThat(runtimeFile.exists()).isTrue()
        assertThat(printableFile.exists()).isTrue()

        val runtime = json.parseToJsonElement(runtimeFile.readText()).jsonObject
        val printable = json.parseToJsonElement(printableFile.readText()).jsonObject

        assertThat(runtime.getValue("squaresX").jsonPrimitive.int)
            .isEqualTo(printable.getValue("squaresX").jsonPrimitive.int)
        assertThat(runtime.getValue("squaresY").jsonPrimitive.int)
            .isEqualTo(printable.getValue("squaresY").jsonPrimitive.int)

        assertThat(runtime.getValue("dictionaryName").jsonPrimitive.content)
            .isEqualTo(printable.getValue("dictionary").jsonPrimitive.content)

        assertThat(runtime.getValue("squareLengthMm").jsonPrimitive.float)
            .isWithin(0.01f)
            .of(printable.getValue("squareSizeMm").jsonPrimitive.float)
        assertThat(runtime.getValue("markerLengthMm").jsonPrimitive.float)
            .isWithin(0.01f)
            .of(printable.getValue("markerSizeMm").jsonPrimitive.float)

        // Sanity: marker must fit inside square (OpenCV CharucoBoard requirement).
        assertThat(runtime.getValue("markerLengthMm").jsonPrimitive.float)
            .isLessThan(runtime.getValue("squareLengthMm").jsonPrimitive.float)
    }

    /**
     * Unit-test CWD is the Gradle `:app` module directory.
     */
    private fun resolveRepoFile(relativeToAppModule: String): File {
        val candidates = listOf(
            File(relativeToAppModule),
            File("app/$relativeToAppModule"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error(
                "Could not find $relativeToAppModule (cwd=${File(".").absolutePath})"
            )
    }
}
