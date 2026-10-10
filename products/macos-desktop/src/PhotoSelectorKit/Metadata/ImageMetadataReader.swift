import Foundation
import ImageIO
import CoreGraphics

/// High-performance native EXIF metadata reader using ImageIO.
public final class ImageMetadataReader: Sendable {

    /// Reads EXIF metadata from an image file at the specified URL.
    /// - Parameter url: The file URL of the image.
    /// - Returns: An `ExifData` struct or `nil` if the image source cannot be read.
    public static func readMetadata(from url: URL) -> ExifData? {
        let sourceOptions: [CFString: Any] = [
            kCGImageSourceShouldCache: false
        ]
        guard let source = CGImageSourceCreateWithURL(url as CFURL, sourceOptions as CFDictionary) else {
            return nil
        }
        return readMetadata(from: source)
    }

    /// Reads EXIF metadata from raw image data in memory.
    /// - Parameter data: Data containing an encoded image.
    /// - Returns: An `ExifData` struct or `nil` if the data cannot be parsed.
    public static func readMetadata(from data: Data) -> ExifData? {
        let sourceOptions: [CFString: Any] = [
            kCGImageSourceShouldCache: false
        ]
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions as CFDictionary) else {
            return nil
        }
        return readMetadata(from: source)
    }

    /// Reads EXIF metadata from an initialized `CGImageSource`.
    /// Merges container-level properties (crucial for camera RAW formats like CR3, ARW, NEF)
    /// and image-level properties at index 0.
    /// - Parameter source: The image source to inspect.
    /// - Returns: An `ExifData` struct or `nil` if properties cannot be extracted.
    public static func readMetadata(from source: CGImageSource) -> ExifData? {
        guard CGImageSourceGetCount(source) > 0 else {
            return nil
        }

        var combinedProperties: [CFString: Any] = [:]

        // 1. Container-level properties (holds metadata for container formats / RAW files)
        if let containerProps = CGImageSourceCopyProperties(source, nil) as? [CFString: Any] {
            combinedProperties.merge(containerProps) { current, _ in current }
        }

        // 2. Image-level properties at index 0
        if let imageProps = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] {
            for (key, value) in imageProps {
                if let dict = value as? [CFString: Any], let existingDict = combinedProperties[key] as? [CFString: Any] {
                    var merged = existingDict
                    merged.merge(dict) { _, new in new }
                    combinedProperties[key] = merged
                } else {
                    combinedProperties[key] = value
                }
            }
        }

        guard !combinedProperties.isEmpty else {
            return nil
        }

        return parseProperties(combinedProperties)
    }

    /// Parses an ImageIO properties dictionary into typed `ExifData`.
    public static func parseProperties(_ properties: [CFString: Any]) -> ExifData {
        let exif = (properties[kCGImagePropertyExifDictionary] ?? properties["{Exif}" as CFString] ?? properties["Exif" as CFString]) as? [CFString: Any]
        let tiff = (properties[kCGImagePropertyTIFFDictionary] ?? properties["{TIFF}" as CFString] ?? properties["TIFF" as CFString]) as? [CFString: Any]
        let exifAux = (properties[kCGImagePropertyExifAuxDictionary] ?? properties["{ExifAux}" as CFString] ?? properties["ExifAux" as CFString]) as? [CFString: Any]

        // 1. Shutter speed (Exposure time in seconds)
        var shutterSpeed: Double?
        let rawExpTime = exif?[kCGImagePropertyExifExposureTime]
            ?? properties[kCGImagePropertyExifExposureTime]
            ?? properties["ExposureTime" as CFString]
        if let rawExpTime {
            shutterSpeed = toDouble(rawExpTime)
        } else if let rawTv = exif?[kCGImagePropertyExifShutterSpeedValue] ?? properties[kCGImagePropertyExifShutterSpeedValue],
                  let tv = toDouble(rawTv) {
            // APEX Tv conversion: time = 2^(-Tv)
            shutterSpeed = pow(2.0, -tv)
        }

        // 2. Aperture (FNumber)
        var aperture: Double?
        let rawFNumber = exif?[kCGImagePropertyExifFNumber]
            ?? properties[kCGImagePropertyExifFNumber]
            ?? properties["FNumber" as CFString]
        if let rawFNumber {
            aperture = toDouble(rawFNumber)
        } else if let rawAv = exif?[kCGImagePropertyExifApertureValue] ?? properties[kCGImagePropertyExifApertureValue],
                  let av = toDouble(rawAv) {
            // APEX Av conversion: f = 2^(Av / 2)
            aperture = pow(2.0, av / 2.0)
        }

        // 3. ISO Sensitivity
        var iso: Double?
        let rawIso = exif?[kCGImagePropertyExifISOSpeedRatings]
            ?? properties[kCGImagePropertyExifISOSpeedRatings]
            ?? exif?["PhotographicSensitivity" as CFString]
            ?? properties["PhotographicSensitivity" as CFString]
        if let rawIso {
            if let array = rawIso as? [Any], let first = array.first {
                iso = toDouble(first)
            } else {
                iso = toDouble(rawIso)
            }
        }

        // 4. Focal Length (mm)
        var focalLength: Double?
        let rawFocalLength = exif?[kCGImagePropertyExifFocalLength]
            ?? properties[kCGImagePropertyExifFocalLength]
            ?? properties["FocalLength" as CFString]
        if let rawFocalLength {
            focalLength = toDouble(rawFocalLength)
        }

        // 5. Lens Model
        var lens = "Unknown"
        let rawLens = exif?[kCGImagePropertyExifLensModel]
            ?? exifAux?[kCGImagePropertyExifAuxLensModel]
            ?? properties[kCGImagePropertyExifLensModel]
            ?? properties["LensModel" as CFString]
            ?? exif?["Lens" as CFString]
            ?? properties["Lens" as CFString]
        if let lensModel = rawLens as? String, !lensModel.trimmingCharacters(in: .whitespaces).isEmpty {
            lens = lensModel
        }

        // 6. Camera Model
        var cameraModel: String?
        let rawModel = (tiff?[kCGImagePropertyTIFFModel] ?? properties[kCGImagePropertyTIFFModel] ?? properties["Model" as CFString]) as? String
        let rawMake = (tiff?[kCGImagePropertyTIFFMake] ?? properties[kCGImagePropertyTIFFMake] ?? properties["Make" as CFString]) as? String

        if let model = rawModel?.trimmingCharacters(in: .whitespaces), !model.isEmpty {
            cameraModel = model
        } else if let make = rawMake?.trimmingCharacters(in: .whitespaces), !make.isEmpty {
            cameraModel = make
        }

        let hasAnyExif = (shutterSpeed != nil || aperture != nil || iso != nil || focalLength != nil || cameraModel != nil || lens != "Unknown")

        return ExifData(
            shutterSpeed: shutterSpeed,
            aperture: aperture,
            iso: iso,
            focalLength: focalLength,
            lens: lens,
            cameraModel: cameraModel,
            isFallback: !hasAnyExif
        )
    }

    // MARK: - Private Helpers

    private static func toDouble(_ value: Any) -> Double? {
        if let d = value as? Double {
            return d
        } else if let f = value as? Float {
            return Double(f)
        } else if let i = value as? Int {
            return Double(i)
        } else if let n = value as? NSNumber {
            return n.doubleValue
        } else if let str = value as? String {
            if let d = Double(str) {
                return d
            }
            // Handle fractional strings like "1/250"
            if str.contains("/") {
                let parts = str.split(separator: "/")
                if parts.count == 2, let num = Double(parts[0]), let den = Double(parts[1]), den > 0 {
                    return num / den
                }
            }
        }
        return nil
    }
}
