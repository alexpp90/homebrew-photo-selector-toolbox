import Foundation

/// The single source of truth for the file formats macOS Desktop recognises.
///
/// Mirrors the cross-product contract in `testdata/formats/manifest.json` (human documentation:
/// `docs/shared/SUPPORTED_FORMATS.md`); `FormatConformanceTests` fails when the two diverge.
/// Scanning, RAW+JPEG companion pairing, the decode strategy and the statistics names all read
/// from here — never keep a private extension list elsewhere.
public enum SupportedFormats {

    /// One photographic format: its display name and every extension it uses (lowercase).
    public struct Format: Sendable, Hashable {
        public let displayName: String
        public let extensions: [String]
        public let isRAW: Bool
    }

    /// Every supported format, RAW first. The first extension of each is canonical.
    public static let all: [Format] = [
        // Camera RAW — required tier (verified against real camera files)
        Format(displayName: "Sony ARW", extensions: ["arw"], isRAW: true),
        Format(displayName: "Canon CR2", extensions: ["cr2"], isRAW: true),
        Format(displayName: "Canon CR3", extensions: ["cr3"], isRAW: true),
        Format(displayName: "Nikon NEF", extensions: ["nef"], isRAW: true),
        Format(displayName: "Nikon NRW", extensions: ["nrw"], isRAW: true),
        Format(displayName: "Fujifilm RAF", extensions: ["raf"], isRAW: true),
        Format(displayName: "Panasonic RW2", extensions: ["rw2"], isRAW: true),
        Format(displayName: "OM System / Olympus ORF", extensions: ["orf"], isRAW: true),
        Format(displayName: "Pentax PEF", extensions: ["pef"], isRAW: true),
        Format(displayName: "Adobe DNG", extensions: ["dng"], isRAW: true),
        Format(displayName: "Samsung SRW", extensions: ["srw"], isRAW: true),
        // Camera RAW — recognised tier (scanned and paired; decoding is best effort)
        Format(displayName: "Hasselblad 3FR", extensions: ["3fr", "fff"], isRAW: true),
        Format(displayName: "Phase One IIQ", extensions: ["iiq"], isRAW: true),
        Format(displayName: "Leica RWL", extensions: ["rwl", "raw"], isRAW: true),
        Format(displayName: "Sony SR2", extensions: ["sr2", "srf"], isRAW: true),
        Format(displayName: "Minolta MRW", extensions: ["mrw"], isRAW: true),
        Format(displayName: "Sigma X3F", extensions: ["x3f"], isRAW: true),
        Format(displayName: "Epson ERF", extensions: ["erf"], isRAW: true),
        Format(displayName: "Mamiya MEF", extensions: ["mef", "mos"], isRAW: true),
        // Rendered bitmaps
        Format(displayName: "JPEG", extensions: ["jpg", "jpeg"], isRAW: false),
        Format(displayName: "Apple HEIC", extensions: ["heic", "heif"], isRAW: false),
        Format(displayName: "PNG", extensions: ["png"], isRAW: false),
        Format(displayName: "TIFF", extensions: ["tif", "tiff"], isRAW: false),
        Format(displayName: "WebP", extensions: ["webp"], isRAW: false)
    ]

    public static let rawExtensions: Set<String> = Set(all.filter(\.isRAW).flatMap(\.extensions))
    public static let bitmapExtensions: Set<String> = Set(all.filter { !$0.isRAW }.flatMap(\.extensions))
    public static let sidecarExtensions: Set<String> = ["xmp"]

    private static let byExtension: [String: Format] = {
        var map: [String: Format] = [:]
        for format in all {
            for ext in format.extensions { map[ext] = format }
        }
        return map
    }()

    /// The format of a file extension (any case, without the dot), or `nil` when unsupported.
    public static func format(forExtension ext: String) -> Format? {
        byExtension[ext.lowercased()]
    }

    /// `true` for every supported photo (RAW or bitmap); sidecars are not photos.
    public static func isPhoto(_ url: URL) -> Bool {
        format(forExtension: url.pathExtension) != nil
    }

    /// `true` for camera RAW files (the primary of a RAW+JPEG pair).
    public static func isRAW(_ url: URL) -> Bool {
        format(forExtension: url.pathExtension)?.isRAW == true
    }
}
