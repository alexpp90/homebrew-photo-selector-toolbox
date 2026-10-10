import Foundation
import Accelerate
import CoreGraphics

/// Result of highlight and shadow clipping analysis.
public struct ExposureMetricResult: Sendable, Equatable, Hashable, Codable {
    /// Percentage of pixels with luminance >= 254 (blown highlights), in range 0.0 ... 100.0.
    public let highlightClippingPercentage: Double

    /// Percentage of pixels with luminance <= 2 (crushed shadows), in range 0.0 ... 100.0.
    public let shadowClippingPercentage: Double

    /// Indicates severe blown highlights (> 5.0% clipped).
    public var hasBlownHighlights: Bool { highlightClippingPercentage > 5.0 }

    /// Indicates severe crushed shadows (> 15.0% clipped).
    public var hasCrushedShadows: Bool { shadowClippingPercentage > 15.0 }

    public init(highlightClippingPercentage: Double, shadowClippingPercentage: Double) {
        self.highlightClippingPercentage = highlightClippingPercentage
        self.shadowClippingPercentage = shadowClippingPercentage
    }
}

/// High-performance exposure clipping evaluation service using Accelerate vImage hardware histogram.
public final class ExposureMetricService: Sendable {

    // MARK: - Asynchronous Public API

    /// Evaluates highlight and shadow clipping asynchronously on a cooperative background thread.
    /// - Parameter cgImage: The image to analyze.
    /// - Returns: An `ExposureMetricResult` or `nil` if image buffer is invalid or dimensions <= 0.
    public static func evaluate(cgImage: CGImage) async -> ExposureMetricResult? {
        await Task.detached(priority: .userInitiated) {
            evaluateSync(cgImage: cgImage)
        }.value
    }

    // MARK: - Synchronous Core API

    /// Evaluates highlight and shadow clipping synchronously on the current thread.
    /// - Parameter cgImage: The image to analyze.
    /// - Returns: An `ExposureMetricResult` or `nil` if image buffer is invalid or dimensions <= 0.
    public static func evaluateSync(cgImage: CGImage) -> ExposureMetricResult? {
        let width = cgImage.width
        let height = cgImage.height
        guard width > 0, height > 0 else { return nil }

        // 1. Grayscale Planar8 conversion via vImage
        let colorSpace = CGColorSpaceCreateDeviceGray()
        var format = vImage_CGImageFormat(
            bitsPerComponent: 8,
            bitsPerPixel: 8,
            colorSpace: Unmanaged.passRetained(colorSpace),
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
            version: 0,
            decode: nil,
            renderingIntent: .defaultIntent
        )

        var src8 = vImage_Buffer()
        guard vImageBuffer_InitWithCGImage(&src8, &format, nil, cgImage, vImage_Flags(kvImageNoFlags)) == kvImageNoError else {
            return nil
        }
        defer { free(src8.data) }

        return computeFromPlanar8(buffer: &src8, width: width, height: height)
    }

    /// Evaluates clipping from an existing Planar8 grayscale buffer (zero-copy pipeline).
    public static func computeFromPlanar8(
        buffer: inout vImage_Buffer,
        width: Int,
        height: Int
    ) -> ExposureMetricResult? {
        let pixelCount = width * height
        guard pixelCount > 0 else { return nil }

        // 2. Hardware SIMD 256-bin histogram calculation (< 1 ms)
        var histogram = [vImagePixelCount](repeating: 0, count: 256)
        let status = histogram.withUnsafeMutableBufferPointer { histBuf in
            vImageHistogramCalculation_Planar8(&buffer, histBuf.baseAddress!, vImage_Flags(kvImageNoFlags))
        }
        guard status == kvImageNoError else { return nil }

        // 3. Highlight clipping: count of pixels >= 254 (bins 254 and 255)
        let highlightCount = Double(histogram[254] + histogram[255])

        // 4. Shadow clipping: count of pixels <= 2 (bins 0, 1, and 2)
        let shadowCount = Double(histogram[0] + histogram[1] + histogram[2])

        let total = Double(pixelCount)
        let highlightPct = min(100.0, max(0.0, ((highlightCount / total) * 1000.0).rounded() / 10.0))
        let shadowPct = min(100.0, max(0.0, ((shadowCount / total) * 1000.0).rounded() / 10.0))

        return ExposureMetricResult(
            highlightClippingPercentage: highlightPct,
            shadowClippingPercentage: shadowPct
        )
    }
}
