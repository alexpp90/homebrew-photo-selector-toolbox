package com.photoselectortoolbox.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanImagesUseCaseTest {

    @Test
    fun minSharpnessThresholdConstantIs40() {
        assertEquals(40.0, ScanImagesUseCase.MIN_SHARPNESS_FOR_AESTHETIC, 1e-9)
    }
}
