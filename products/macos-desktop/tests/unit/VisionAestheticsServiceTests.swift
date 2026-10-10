import Testing
import Foundation
import CoreGraphics
@testable import PhotoSelectorKit

@Suite("VisionAestheticsService Unit Tests")
struct VisionAestheticsServiceTests {

    @Test("REQ-MAC-SCORE.03: Valid CGImage buffer evaluation completes with valid aesthetic scores")
    func testEvaluateValidImageBuffer() async throws {
        let size = 128
        let pixels = [UInt8](repeating: 150, count: size * size)
        guard let image = SyntheticImageFactory.createGrayscaleCGImage(width: size, height: size, pixels: pixels) else {
            Issue.record("Failed to create synthetic test image")
            return
        }

        let scorer = VisionAestheticsService()
        if #available(macOS 15.0, *) {
            do {
                let evaluation = try await scorer.evaluate(cgImage: image)
                #expect(evaluation.overallScore >= -1.0 && evaluation.overallScore <= 1.0)
                #expect(evaluation.mappedScore >= 1.0 && evaluation.mappedScore <= 10.0)
            } catch VisionAestheticsError.unsupportedOS {
                // macOS < 15 environment fallback accepted
            }
        }
    }

    @Test("Invalid file URL throws unreadableImage error")
    func testEvaluateInvalidURL() async {
        let scorer = VisionAestheticsService()
        let nonExistentURL = URL(fileURLWithPath: "/tmp/non_existent_file_\(UUID().uuidString).jpg")

        if #available(macOS 15.0, *) {
            do {
                _ = try await scorer.evaluate(url: nonExistentURL)
                Issue.record("Expected error for non-existent file URL")
            } catch let error as VisionAestheticsError {
                #expect(error == .unreadableImage(nonExistentURL))
            } catch {
                #expect(!error.localizedDescription.isEmpty)
            }
        }
    }

    @Test("Aesthetic score mapping scale bounds verification")
    func testScoreMappingScale() {
        let minEvaluation = AestheticEvaluation(overallScore: -1.0, isUtility: false)
        #expect(minEvaluation.mappedScore == 1.0)

        let maxEvaluation = AestheticEvaluation(overallScore: 1.0, isUtility: false)
        #expect(maxEvaluation.mappedScore == 10.0)

        let midEvaluation = AestheticEvaluation(overallScore: 0.0, isUtility: false)
        #expect(midEvaluation.mappedScore == 5.5)
    }
}
