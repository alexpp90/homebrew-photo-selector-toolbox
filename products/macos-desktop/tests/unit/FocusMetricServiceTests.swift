import Testing
import Foundation
import CoreGraphics
@testable import PhotoSelectorKit

@Suite("FocusMetricService Unit Tests")
struct FocusMetricServiceTests {

    @Test("REQ-MAC-SCORE.01: Uniform blank image produces zero focus score and Blurry classification")
    func testUniformBlankImage() {
        let size = 256
        let pixels = [UInt8](repeating: 128, count: size * size)
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create synthetic blank image")
            return
        }

        let result = FocusMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect(result?.score == 0.0)
        #expect(result?.rawVariance == 0.0)
        #expect(result?.category == .blurry)
    }

    @Test("Sharp checkerboard pattern produces high focus score >= 70.0 (Sharp)")
    func testSharpCheckerboard() {
        let size = 256
        var pixels = [UInt8](repeating: 0, count: size * size)
        for y in 0..<size {
            for x in 0..<size {
                let block = ((x / 16) + (y / 16)) % 2
                pixels[y * size + x] = block == 0 ? 20 : 235
            }
        }
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create synthetic checkerboard image")
            return
        }

        let result = FocusMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect((result?.score ?? 0.0) >= 70.0)
        #expect(result?.category == .sharp)
    }

    @Test("High-ISO sensor noise on blank image is suppressed by noise-floor correction")
    func testSensorNoiseSuppression() {
        let size = 256
        var pixels = [UInt8](repeating: 0, count: size * size)
        for i in 0..<(size * size) {
            let noise = Int.random(in: -20...20)
            pixels[i] = UInt8(min(255, max(0, 128 + noise)))
        }
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create noisy image")
            return
        }

        let result = FocusMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        // High ISO sensor noise alone must NOT falsely score as acceptable or sharp
        #expect((result?.score ?? 100.0) < 35.0)
        #expect(result?.category == .blurry)
    }

    @Test("Sharp edges with added sensor noise preserve high sharpness score")
    func testSharpEdgesWithNoisePreservation() {
        let size = 256
        var pixels = [UInt8](repeating: 0, count: size * size)
        for y in 0..<size {
            for x in 0..<size {
                let block = ((x / 16) + (y / 16)) % 2
                let base = block == 0 ? 20 : 235
                let noise = Int.random(in: -15...15)
                pixels[y * size + x] = UInt8(min(255, max(0, base + noise)))
            }
        }
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create sharp noisy image")
            return
        }

        let result = FocusMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect((result?.score ?? 0.0) >= 70.0)
        #expect(result?.category == .sharp)
    }

    @Test("Center crop ROI focus evaluation ignores peripheral edges")
    func testCenterCropROIEvaluation() {
        let size = 256
        var pixels = [UInt8](repeating: 128, count: size * size)
        // Draw sharp high-contrast border on outer 10%
        for y in 0..<size {
            for x in 0..<size {
                if x < 15 || x > 240 || y < 15 || y > 240 {
                    pixels[y * size + x] = (x % 2 == 0) ? 0 : 255
                }
            }
        }
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create border image")
            return
        }

        // With center crop enabled, outer border is excluded: score should be 0.0
        let resultCropped = FocusMetricService.evaluateSync(cgImage: image, cropToCenter: true)
        #expect(resultCropped != nil)
        #expect(resultCropped?.score == 0.0)
        #expect(resultCropped?.category == .blurry)
    }

    @Test("PROJECT.md Contract: computeLaplacianVariance returns raw variance")
    func testProjectContractCompatibility() {
        let size = 64
        let pixels = [UInt8](repeating: 128, count: size * size)
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create image")
            return
        }
        let variance = FocusMetricService.computeLaplacianVariance(cgImage: image)
        #expect(variance != nil)
        #expect(variance == 0.0)
    }

    @Test("Tiny image buffers (< 3x3) safely return nil")
    func testTinyBufferSafety() {
        let pixels = [UInt8](repeating: 128, count: 4)
        guard let image2x2 = SyntheticImageFactory.createGrayscaleCGImage(width: 2, height: 2, pixels: pixels) else {
            Issue.record("Failed to create 2x2 image")
            return
        }
        let result = FocusMetricService.evaluateSync(cgImage: image2x2)
        #expect(result == nil)
    }

    // MARK: - Row Padding & Memory Alignment Regressions

    @Test("Non-power-of-two 100x100 uniform blank image produces zero score and Blurry classification")
    func testNonPowerOfTwo100x100UniformBlankImage() {
        let size = 100
        for val in [UInt8(0), UInt8(128), UInt8(255)] {
            let pixels = [UInt8](repeating: val, count: size * size)
            guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
                Issue.record("Failed to create synthetic image for size 100")
                return
            }
            let result = FocusMetricService.evaluateSync(cgImage: image)
            #expect(result != nil)
            #expect(result?.score == 0.0, "Score should be 0.0 for uniform value \(val)")
            #expect(result?.rawVariance == 0.0, "Raw variance should be 0.0")
            #expect(result?.category == .blurry)
        }
    }

    @Test("Non-power-of-two image dimensions with row padding compute valid sharpness scores")
    func testNonPowerOfTwoDimensionsWithRowPadding() {
        let width = 100
        let height = 100
        var pixels = [UInt8](repeating: 0, count: width * height)

        // Draw sharp high-contrast grid pattern
        for y in 0..<height {
            for x in 0..<width {
                let block = ((x / 10) + (y / 10)) % 2
                pixels[y * width + x] = block == 0 ? 15 : 240
            }
        }

        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: width, height: height, pixels: pixels) else {
            Issue.record("Failed to create 100x100 synthetic image")
            return
        }

        let result = FocusMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect((result?.score ?? 0.0) >= 70.0, "Score \(result?.score ?? 0) was degraded by unhandled row padding in 100x100 image!")
        #expect(result?.category == .sharp)
    }

    @Test("Sharp features located exclusively in bottom rows of padded buffers are detected")
    func testBottomRowsSharpFeaturesInPaddedBuffer() {
        let width = 100
        let height = 100
        var pixels = [UInt8](repeating: 128, count: width * height)

        for y in 85..<100 {
            for x in 0..<width {
                pixels[y * width + x] = (x % 4 == 0) ? 0 : 255
            }
        }

        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: width, height: height, pixels: pixels) else {
            Issue.record("Failed to create bottom-row test image")
            return
        }

        let result = FocusMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect((result?.score ?? 0.0) > 30.0, "Sharp features in bottom rows were completely undetected due to rowBytes stride truncation!")
    }

    @Test("Arbitrary camera aspect ratios (e.g. 300x200, 101x53) do not crash or corrupt variance")
    func testArbitraryCameraDimensions() {
        let testDimensions = [(300, 200), (101, 53), (150, 150)]

        for (w, h) in testDimensions {
            let pixels = [UInt8](repeating: 128, count: w * h)
            guard let img = SyntheticImageFactory.createGrayscaleCGImage(width: w, height: h, pixels: pixels) else {
                Issue.record("Failed to create \(w)x\(h) image")
                continue
            }

            let result = FocusMetricService.evaluateSync(cgImage: img)
            #expect(result != nil)
            #expect(result?.score == 0.0, "Uniform image of size \(w)x\(h) should yield score 0.0")
            #expect(result?.category == .blurry)
        }
    }
}
