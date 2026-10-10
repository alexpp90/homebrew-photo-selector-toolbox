import Testing
import Foundation
import ImageIO
@testable import PhotoSelectorKit

@Suite("ImageMetadataReader Unit Tests")
struct ImageMetadataReaderTests {

    @Test("REQ-MAC-EXIF.01: Synthetic JPEG with genuine EXIF & TIFF metadata correctly parsed")
    func testReadMetadataFromSyntheticJPEG() {
        let exifProps: [CFString: Any] = [
            kCGImagePropertyExifExposureTime: 0.005,
            kCGImagePropertyExifFNumber: 2.8,
            kCGImagePropertyExifISOSpeedRatings: [100],
            kCGImagePropertyExifFocalLength: 50.0,
            kCGImagePropertyExifLensModel: "FE 50mm F1.2 GM"
        ]

        let tiffProps: [CFString: Any] = [
            kCGImagePropertyTIFFModel: "ILCE-7RM4"
        ]

        guard let data = SyntheticImageFactory.createJPEGDataWithMetadata(
            width: 64,
            height: 64,
            exifProperties: exifProps,
            tiffProperties: tiffProps
        ) else {
            Issue.record("Failed to create synthetic JPEG with metadata")
            return
        }

        let metadata = ImageMetadataReader.readMetadata(from: data)
        #expect(metadata != nil)
        #expect(metadata?.shutterSpeed == 0.005)
        #expect(metadata?.formattedShutterSpeed == "1/200s")
        #expect(metadata?.aperture == 2.8)
        #expect(metadata?.formattedAperture == "f/2.8")
        #expect(metadata?.iso == 100.0)
        #expect(metadata?.formattedISO == "ISO 100")
        #expect(metadata?.focalLength == 50.0)
        #expect(metadata?.formattedFocalLength == "50mm")
        #expect(metadata?.lens == "FE 50mm F1.2 GM")
        #expect(metadata?.cameraModel == "ILCE-7RM4")
        #expect(metadata?.isFallback == false)
    }

    @Test("Image without metadata defaults to fallback state")
    func testFallbackWhenNoExifPresent() {
        guard let data = SyntheticImageFactory.createJPEGDataWithMetadata(
            width: 32,
            height: 32,
            exifProperties: [:],
            tiffProperties: [:]
        ) else {
            Issue.record("Failed to create bare JPEG")
            return
        }

        let metadata = ImageMetadataReader.readMetadata(from: data)
        #expect(metadata != nil)
        #expect(metadata?.isFallback == true)
        #expect(metadata?.lens == "Unknown")
        #expect(metadata?.shutterSpeed == nil)
        #expect(metadata?.formattedShutterSpeed == "Unknown")
        #expect(metadata?.aperture == nil)
        #expect(metadata?.formattedAperture == "Unknown")
    }

    @Test("REQ-MAC-EXIF.03: Formatted metadata helper edge cases")
    func testFormattedMetadataHelperEdgeCases() {
        let wholeSeconds = ExifData(shutterSpeed: 2.0, aperture: 1.4, focalLength: 85.0)
        #expect(wholeSeconds.formattedShutterSpeed == "2s")
        #expect(wholeSeconds.formattedAperture == "f/1.4")
        #expect(wholeSeconds.formattedFocalLength == "85mm")

        let fractionalSeconds = ExifData(shutterSpeed: 1.5)
        #expect(fractionalSeconds.formattedShutterSpeed == "1.5s")

        let unknownExif = ExifData()
        #expect(unknownExif.formattedShutterSpeed == "Unknown")
        #expect(unknownExif.formattedAperture == "Unknown")
        #expect(unknownExif.formattedFocalLength == "Unknown")
        #expect(unknownExif.formattedISO == "Unknown")
        #expect(unknownExif.formattedSummary == "No EXIF")
    }

    @Test("REQ-MAC-EXIF.02: APEX shutter speed and aperture conversion")
    func testApexConversion() {
        let props: [CFString: Any] = [
            kCGImagePropertyExifDictionary: [
                kCGImagePropertyExifShutterSpeedValue: 8.0, // 2^(-8) = 0.00390625 s (1/256s)
                kCGImagePropertyExifApertureValue: 2.0 // 2^(2/2) = f/2.0
            ]
        ]
        let exif = ImageMetadataReader.parseProperties(props)
        #expect(exif.shutterSpeed != nil)
        #expect(exif.formattedShutterSpeed == "1/256s")
        #expect(exif.aperture != nil)
        #expect(exif.formattedAperture == "f/2.0")
    }

    @Test("Camera model preservation and make fallback")
    func testCameraMakeAndModelFormatting() {
        let props: [CFString: Any] = [
            kCGImagePropertyTIFFDictionary: [
                kCGImagePropertyTIFFMake: "Sony",
                kCGImagePropertyTIFFModel: "ILCE-7M4"
            ]
        ]
        let exif = ImageMetadataReader.parseProperties(props)
        #expect(exif.cameraModel == "ILCE-7M4")

        // Fallback to make when model is absent
        let makeOnlyProps: [CFString: Any] = [
            kCGImagePropertyTIFFDictionary: [
                kCGImagePropertyTIFFMake: "Nikon"
            ]
        ]
        let makeOnlyExif = ImageMetadataReader.parseProperties(makeOnlyProps)
        #expect(makeOnlyExif.cameraModel == "Nikon")
    }

    @Test("FormattedSummary produces concise optical string")
    func testFormattedSummary() {
        let exif = ExifData(
            shutterSpeed: 0.005,
            aperture: 2.8,
            iso: 100,
            focalLength: 50
        )
        #expect(exif.formattedSummary == "1/200s · f/2.8 · ISO 100 · 50mm")
    }
}
