import Foundation

/// Typed EXIF metadata extracted from an image.
public struct ExifData: Sendable, Codable, Equatable, Hashable {
    public let shutterSpeed: Double?
    public let aperture: Double?
    public let iso: Double?
    public let focalLength: Double?
    public let lens: String
    public let cameraModel: String?
    public let isFallback: Bool

    public init(
        shutterSpeed: Double? = nil,
        aperture: Double? = nil,
        iso: Double? = nil,
        focalLength: Double? = nil,
        lens: String = "Unknown",
        cameraModel: String? = nil,
        isFallback: Bool = false
    ) {
        self.shutterSpeed = shutterSpeed
        self.aperture = aperture
        self.iso = iso
        self.focalLength = focalLength
        self.lens = lens
        self.cameraModel = cameraModel
        self.isFallback = isFallback
    }

    /// Formatted shutter speed (e.g. "1/200s", "2s", "1.5s", or "Unknown").
    public var formattedShutterSpeed: String {
        formattedShutterSpeedOrNil ?? "Unknown"
    }

    public var formattedShutterSpeedOrNil: String? {
        guard let speed = shutterSpeed, speed > 0 else { return nil }
        if speed < 1.0 {
            let denominator = Int((1.0 / speed).rounded())
            return "1/\(denominator)s"
        } else if speed.truncatingRemainder(dividingBy: 1.0) == 0 {
            return "\(Int(speed))s"
        } else {
            return String(format: "%.1fs", speed)
        }
    }

    /// Formatted aperture (e.g. "f/2.8", "f/1.4", or "Unknown").
    public var formattedAperture: String {
        formattedApertureOrNil ?? "Unknown"
    }

    public var formattedApertureOrNil: String? {
        guard let ap = aperture, ap > 0 else { return nil }
        return String(format: "f/%.1f", ap)
    }

    /// Formatted focal length (e.g. "50mm", "24mm", or "Unknown").
    public var formattedFocalLength: String {
        formattedFocalLengthOrNil ?? "Unknown"
    }

    public var formattedFocalLengthOrNil: String? {
        guard let fl = focalLength, fl > 0 else { return nil }
        return String(format: "%.0fmm", fl)
    }

    /// Formatted ISO sensitivity (e.g. "ISO 100", "ISO 6400", or "Unknown").
    public var formattedISO: String {
        formattedISOOrNil ?? "Unknown"
    }

    public var formattedISOOrNil: String? {
        guard let isoValue = iso, isoValue > 0 else { return nil }
        return "ISO \(Int(isoValue))"
    }

    /// Formatted concise summary of available optical exposure settings (e.g. "1/2000s · f/1.4 · ISO 100 · 85mm").
    /// Skips missing/unknown values cleanly without "Unknown" placeholders.
    public var formattedSummary: String {
        var parts: [String] = []
        if let speed = formattedShutterSpeedOrNil { parts.append(speed) }
        if let ap = formattedApertureOrNil { parts.append(ap) }
        if let isoVal = formattedISOOrNil { parts.append(isoVal) }
        if let fl = formattedFocalLengthOrNil { parts.append(fl) }
        return parts.isEmpty ? "No EXIF" : parts.joined(separator: " · ")
    }
}
