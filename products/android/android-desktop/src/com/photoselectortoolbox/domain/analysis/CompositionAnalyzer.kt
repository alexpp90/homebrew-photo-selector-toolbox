package com.photoselectortoolbox.domain.analysis

import android.graphics.Bitmap
import com.photoselectortoolbox.domain.model.CompositionResult

/**
 * Asynchronous analyzer interface for photographic composition.
 */
interface CompositionAnalyzer {
    /**
     * Analyzes the composition of the provided [bitmap], extracting leading lines,
     * visual mass centroid, and 3x3 grid mass distribution.
     *
     * @param bitmap The image bitmap to evaluate.
     * @return The calculated [CompositionResult].
     */
    suspend fun analyze(bitmap: Bitmap): CompositionResult
}
