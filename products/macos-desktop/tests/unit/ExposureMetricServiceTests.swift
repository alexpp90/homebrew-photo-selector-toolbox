import Testing
import Foundation
import CoreGraphics
@testable import PhotoSelectorKit

@Suite("ExposureMetricService Unit Tests")
struct ExposureMetricServiceTests {

    @Test("REQ-MAC-SCORE.02: Pure white image detects 100% highlight clipping and 0% shadow clipping")
    func testPureWhiteImage() {
        let size = 128
        let pixels = [UInt8](repeating: 255, count: size * size)
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create white image")
            return
        }

        let result = ExposureMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect(result?.highlightClippingPercentage == 100.0)
        #expect(result?.shadowClippingPercentage == 0.0)
        #expect(result?.hasBlownHighlights == true)
        #expect(result?.hasCrushedShadows == false)
    }

    @Test("Pure black image detects 100% shadow clipping and 0% highlight clipping")
    func testPureBlackImage() {
        let size = 128
        let pixels = [UInt8](repeating: 0, count: size * size)
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create black image")
            return
        }

        let result = ExposureMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect(result?.highlightClippingPercentage == 0.0)
        #expect(result?.shadowClippingPercentage == 100.0)
        #expect(result?.hasBlownHighlights == false)
        #expect(result?.hasCrushedShadows == true)
    }

    @Test("Mid-tone gray image detects zero clipping")
    func testMidToneGrayImage() {
        let size = 128
        let pixels = [UInt8](repeating: 128, count: size * size)
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create gray image")
            return
        }

        let result = ExposureMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect(result?.highlightClippingPercentage == 0.0)
        #expect(result?.shadowClippingPercentage == 0.0)
        #expect(result?.hasBlownHighlights == false)
        #expect(result?.hasCrushedShadows == false)
    }

    @Test("Exact threshold boundary verification: highlights >= 254, shadows <= 2")
    func testExactThresholdBoundaries() {
        var pixels = [UInt8](repeating: 128, count: 100)
        // 10 pixels of 254, 10 pixels of 255 -> 20% highlights
        for i in 0..<10 { pixels[i] = 254 }
        for i in 10..<20 { pixels[i] = 255 }
        // 10 pixels of 0, 10 pixels of 1, 10 pixels of 2 -> 30% shadows
        for i in 20..<30 { pixels[i] = 0 }
        for i in 30..<40 { pixels[i] = 1 }
        for i in 40..<50 { pixels[i] = 2 }
        // boundary non-clipped pixels: 3 and 253
        pixels[50] = 3
        pixels[51] = 253

        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: 10, height: 10, pixels: pixels) else {
            Issue.record("Failed to create boundary image")
            return
        }

        let result = ExposureMetricService.evaluateSync(cgImage: image)
        #expect(result != nil)
        #expect(result?.highlightClippingPercentage == 20.0)
        #expect(result?.shadowClippingPercentage == 30.0)
    }
}
