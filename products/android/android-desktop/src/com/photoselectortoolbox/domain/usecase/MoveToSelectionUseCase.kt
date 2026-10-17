package com.photoselectortoolbox.domain.usecase

import com.photoselectortoolbox.data.repository.FileOperationResult
import com.photoselectortoolbox.data.repository.ImageRepository

/**
 * Result of a move/copy operation for a single image.
 *
 * This was the first result shape in the product to carry the destination URI,
 * and it is now *the* shape: [ImageRepository.moveImage]/`copyImage` return the
 * same type instead of a `Boolean` that discarded where the file went. Kept as
 * an alias rather than a second data class so there is one result to reason
 * about, and so existing call sites and tests are untouched.
 */
typealias MoveResult = FileOperationResult
