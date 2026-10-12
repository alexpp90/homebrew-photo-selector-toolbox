package com.photoselectortoolbox.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * A line segment detected via Hough Line Transform, normalized to [0.0, 1.0].
 *
 * @property startX Normalized X coordinate of the starting point (0.0 = left, 1.0 = right).
 * @property startY Normalized Y coordinate of the starting point (0.0 = top, 1.0 = bottom).
 * @property endX Normalized X coordinate of the ending point.
 * @property endY Normalized Y coordinate of the ending point.
 * @property strength Line confidence and relative length score in [0.0, 1.0].
 */
data class DetectedLine(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val strength: Float
)

/**
 * Result of computer vision composition analysis for a photograph.
 *
 * @property leadingLines Detected perspective and leading lines, ordered by strength.
 * @property massCentroidX Normalized X coordinate of the visual mass / edge energy centroid [0.0, 1.0].
 * @property massCentroidY Normalized Y coordinate of the visual mass / edge energy centroid [0.0, 1.0].
 * @property gridMassDistribution Normalized visual mass percentage across 3x3 cells (length 9, summing to 1.0).
 */
data class CompositionResult(
    val leadingLines: List<DetectedLine>,
    val massCentroidX: Float,
    val massCentroidY: Float,
    val gridMassDistribution: List<Float>
) {
    /**
     * Serializes this composition result into a JSON string for database caching.
     */
    fun toJson(): String {
        val root = JSONObject()

        val linesArray = JSONArray()
        for (line in leadingLines) {
            val lineObj = JSONObject()
            lineObj.put("startX", line.startX.toDouble())
            lineObj.put("startY", line.startY.toDouble())
            lineObj.put("endX", line.endX.toDouble())
            lineObj.put("endY", line.endY.toDouble())
            lineObj.put("strength", line.strength.toDouble())
            linesArray.put(lineObj)
        }
        root.put("leadingLines", linesArray)

        root.put("massCentroidX", massCentroidX.toDouble())
        root.put("massCentroidY", massCentroidY.toDouble())

        val gridArray = JSONArray()
        for (mass in gridMassDistribution) {
            gridArray.put(mass.toDouble())
        }
        root.put("gridMassDistribution", gridArray)

        return root.toString()
    }

    companion object {
        /**
         * Deserializes a [CompositionResult] from a JSON string.
         */
        fun fromJson(json: String): CompositionResult {
            val root = JSONObject(json)

            val linesArray = root.optJSONArray("leadingLines")
            val lines = mutableListOf<DetectedLine>()
            if (linesArray != null) {
                for (i in 0 until linesArray.length()) {
                    val lineObj = linesArray.getJSONObject(i)
                    lines.add(
                        DetectedLine(
                            startX = lineObj.optDouble("startX", 0.0).toFloat(),
                            startY = lineObj.optDouble("startY", 0.0).toFloat(),
                            endX = lineObj.optDouble("endX", 0.0).toFloat(),
                            endY = lineObj.optDouble("endY", 0.0).toFloat(),
                            strength = lineObj.optDouble("strength", 0.0).toFloat()
                        )
                    )
                }
            }

            val massCentroidX = root.optDouble("massCentroidX", 0.5).toFloat()
            val massCentroidY = root.optDouble("massCentroidY", 0.5).toFloat()

            val gridArray = root.optJSONArray("gridMassDistribution")
            val grid = mutableListOf<Float>()
            if (gridArray != null) {
                for (i in 0 until gridArray.length()) {
                    grid.add(gridArray.optDouble(i, 0.0).toFloat())
                }
            } else {
                repeat(9) { grid.add(1f / 9f) }
            }

            return CompositionResult(
                leadingLines = lines,
                massCentroidX = massCentroidX,
                massCentroidY = massCentroidY,
                gridMassDistribution = grid
            )
        }
    }
}
