package com.photoselectortoolbox.domain.usecase

import android.content.Context
import android.net.Uri
import com.photoselectortoolbox.domain.duplicates.DuplicateDetectionProgress
import com.photoselectortoolbox.domain.duplicates.DuplicateDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case wrapper for duplicate file detection.
 * Delegates to [DuplicateDetector] and exposes progress via Flow.
 */
class FindDuplicatesUseCase @Inject constructor(
    private val duplicateDetector: DuplicateDetector,
    @ApplicationContext private val context: Context
) {

    /**
     * Find duplicate files among the given URIs with progress reporting.
     *
     * @param uris List of pairs: (content URI, file size in bytes).
     * @return Flow emitting [DuplicateDetectionProgress] updates.
     */
    operator fun invoke(
        uris: List<Pair<Uri, Long>>
    ): Flow<DuplicateDetectionProgress> {
        return duplicateDetector.findDuplicatesWithProgress(uris, context)
    }
}
