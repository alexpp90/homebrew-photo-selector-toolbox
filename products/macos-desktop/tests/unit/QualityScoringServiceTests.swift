import Testing
import Foundation
import CoreGraphics
@testable import PhotoSelectorKit

@Suite("QualityScoringService Unified Pipeline Unit Tests")
struct QualityScoringServiceTests {

    @Test("REQ-MAC-SCORE.01, REQ-MAC-SCORE.03: Sharp checkerboard CGImage yields high sharpness score and .sharp category")
    func testSharpCheckerboardScoring() async {
        let size = 256
        var pixels = [UInt8](repeating: 0, count: size * size)
        for y in 0..<size {
            for x in 0..<size {
                let block = ((x / 16) + (y / 16)) % 2
                pixels[y * size + x] = block == 0 ? 20 : 235
            }
        }
        guard let cgImage = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create synthetic sharp checkerboard CGImage")
            return
        }

        let scores = await QualityScoringService.score(cgImage: cgImage)
        #expect(scores.sharpness >= 70.0)
        #expect(scores.focusCategory == .sharp)
        #expect(scores.highlightClipping == 0.0)
        #expect(scores.shadowClipping == 0.0)
    }

    @Test("REQ-MAC-SCORE.01: Solid uniform blurry CGImage yields zero sharpness score and .blurry category")
    func testSolidBlurryScoring() async {
        let size = 256
        let pixels = [UInt8](repeating: 128, count: size * size)
        guard let cgImage = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create synthetic uniform blurry CGImage")
            return
        }

        let scores = await QualityScoringService.score(cgImage: cgImage)
        #expect(scores.sharpness == 0.0)
        #expect(scores.noise == 0.0)
        #expect(scores.focusCategory == .blurry)
        #expect(scores.highlightClipping == 0.0)
        #expect(scores.shadowClipping == 0.0)
    }

    @Test("REQ-MAC-SCORE.02: Highlight-clipped pure white CGImage yields 100% highlight clipping")
    func testHighlightClippedWhiteScoring() async {
        let size = 128
        let pixels = [UInt8](repeating: 255, count: size * size)
        guard let cgImage = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create synthetic pure white CGImage")
            return
        }

        let scores = await QualityScoringService.score(cgImage: cgImage)
        #expect(scores.highlightClipping == 100.0)
        #expect(scores.shadowClipping == 0.0)
        #expect(scores.sharpness == 0.0)
        #expect(scores.focusCategory == .blurry)
    }

    @Test("REQ-MAC-SCORE.02: Shadow-clipped pure black CGImage yields 100% shadow clipping")
    func testShadowClippedBlackScoring() async {
        let size = 128
        let pixels = [UInt8](repeating: 0, count: size * size)
        guard let cgImage = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create synthetic pure black CGImage")
            return
        }

        let scores = await QualityScoringService.score(cgImage: cgImage)
        #expect(scores.shadowClipping == 100.0)
        #expect(scores.highlightClipping == 0.0)
        #expect(scores.sharpness == 0.0)
        #expect(scores.focusCategory == .blurry)
    }
}
