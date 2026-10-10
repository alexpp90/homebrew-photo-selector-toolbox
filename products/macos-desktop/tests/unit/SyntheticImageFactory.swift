import Foundation
import CoreGraphics
import ImageIO

enum SyntheticImageFactory {
    /// Creates a grayscale CGImage from raw pixel byte buffer.
    static func createGrayscaleCGImage(width: Int, height: Int, pixels: [UInt8]) -> CGImage? {
        guard pixels.count >= width * height else { return nil }
        let colorSpace = CGColorSpaceCreateDeviceGray()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue)
        guard let provider = CGDataProvider(data: Data(pixels) as CFData) else { return nil }
        return CGImage(
            width: width,
            height: height,
            bitsPerComponent: 8,
            bitsPerPixel: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: bitmapInfo,
            provider: provider,
            decode: nil,
            shouldInterpolate: false,
            intent: .defaultIntent
        )
    }

    /// Creates an in-memory JPEG Data payload with genuine EXIF and TIFF metadata tags.
    static func createJPEGDataWithMetadata(
        width: Int = 64,
        height: Int = 64,
        exifProperties: [CFString: Any] = [:],
        tiffProperties: [CFString: Any] = [:]
    ) -> Data? {
        let pixels = [UInt8](repeating: 128, count: width * height)
        guard let cgImage = createGrayscaleCGImage(width: width, height: height, pixels: pixels) else {
            return nil
        }
        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            data as CFMutableData,
            "public.jpeg" as CFString,
            1,
            nil
        ) else {
            return nil
        }

        var properties: [CFString: Any] = [:]
        if !exifProperties.isEmpty {
            properties[kCGImagePropertyExifDictionary] = exifProperties
        }
        if !tiffProperties.isEmpty {
            properties[kCGImagePropertyTIFFDictionary] = tiffProperties
        }

        CGImageDestinationAddImage(destination, cgImage, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return nil }
        return data as Data
    }
}
