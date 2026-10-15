import Testing
import Foundation
import CoreGraphics
@testable import PhotoSelectorKit

// Format conformance against the shared, machine-readable contract in
// `testdata/formats/manifest.json` (human documentation: docs/shared/SUPPORTED_FORMATS.md).
//
// Every check goes through the code the app really uses:
//  - discovery and RAW+JPEG grouping: `DirectoryScanner.scanDirectory` / `FileCullingManager`
//  - display: `ThumbnailLoader.progressivePreview(for:)` (what `ZoomableImageViewport` renders)
//  - EXIF: `CullingWorkspaceViewModel.readMetadataWithCompanionFallback` → `ImageMetadataReader`
//  - scoring: `QualityScoringService.score(cgImage:)` on the displayed preview (as the viewport does)
//
// The RAW corpus is not committed. Fetch it with `python3 scripts/fetch_format_corpus.py`;
// without it the corpus test is skipped, unless `PST_REQUIRE_FORMAT_CORPUS=1` makes it fail.

// MARK: - Manifest model

struct FormatManifest: Decodable, Sendable {
    struct Corpus: Decodable, Sendable {
        let cache_env: String
        let require_env: String
        let default_cache_dir: String
        let fetch_script: String
    }
    struct Display: Decodable, Sendable { let min_long_edge_px: Int }
    struct Tolerance: Decodable, Sendable { let relative: Double }
    struct Format: Decodable, Sendable {
        let id: String
        let tier: String
        let kind: String
        let extensions: [String]
    }
    struct Expect: Decodable, Sendable {
        let long_edge_px: Int
        let make: String
        let model: String
        let iso: Double
        let exposure_time_s: Double
        let f_number: Double
        let focal_length_mm: Double
        let lens: String?
    }
    struct Sample: Decodable, Sendable {
        let id: String
        let format: String
        let file: String
        let source: String
        let expect: Expect
    }

    let corpus: Corpus
    let display: Display
    let exif_tolerance: Tolerance
    let formats: [Format]
    let samples: [Sample]

    /// `testdata/formats/` found by walking up from this source file.
    static let directory: URL = {
        var dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while dir.path != "/" {
            let candidate = dir.appendingPathComponent("testdata/formats/manifest.json")
            if FileManager.default.fileExists(atPath: candidate.path) {
                return candidate.deletingLastPathComponent()
            }
            dir = dir.deletingLastPathComponent()
        }
        fatalError("testdata/formats/manifest.json not found above \(#filePath)")
    }()

    static let shared: FormatManifest = {
        let data = try! Data(contentsOf: directory.appendingPathComponent("manifest.json"))
        return try! JSONDecoder().decode(FormatManifest.self, from: data)
    }()

    func format(_ id: String) -> Format { formats.first { $0.id == id }! }

    var corpusDirectory: URL {
        let env = ProcessInfo.processInfo.environment
        if let custom = env[corpus.cache_env], !custom.isEmpty {
            return URL(fileURLWithPath: (custom as NSString).expandingTildeInPath)
        }
        return URL(fileURLWithPath: (corpus.default_cache_dir as NSString).expandingTildeInPath)
    }

    var corpusRequired: Bool { ProcessInfo.processInfo.environment[corpus.require_env] == "1" }

    var corpusSamples: [Sample] { samples.filter { $0.source == "corpus" } }
    var repoSamples: [Sample] { samples.filter { $0.source == "repo" } }

    var missingCorpusFiles: [String] {
        corpusSamples.map(\.file).filter {
            !FileManager.default.fileExists(atPath: corpusDirectory.appendingPathComponent($0).path)
        }
    }
}

// MARK: - Known gaps (strict)

/// Capabilities that genuinely cannot pass on macOS for a sample. Strict: a listed gap that
/// starts passing fails the test, so this list can only shrink. Keep it empty if possible.
enum FormatKnownGaps {
    /// sampleID → capability → reason
    static let gaps: [String: [String: String]] = [:]
}

// MARK: - Shared probes (the app's real code paths)

enum FormatProbe {
    static func tempFolder(_ label: String) throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .standardizedFileURL.resolvingSymlinksInPath()
            .appendingPathComponent("format-conformance-\(label)-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func scan(_ folder: URL) async throws -> [PhotoItem] {
        let defaults = UserDefaults(suiteName: "format-conformance-\(UUID().uuidString)")!
        return try await DirectoryScanner(userDefaults: defaults).scanDirectory(at: folder, recursive: false)
    }

    /// The image the culling viewport finally shows (last tier of the progressive stream).
    static func displayImage(_ url: URL) async -> CGImage? {
        let loader = ThumbnailLoader(cache: ThumbnailCache())
        var last: CGImage?
        for await progressive in loader.progressivePreview(for: url) {
            last = progressive.image
        }
        return last
    }

    static func close(_ actual: Double?, _ expected: Double, _ tolerance: Double) -> Bool {
        guard let actual else { return false }
        return abs(actual - expected) <= abs(expected) * tolerance
    }

    static func same(_ actual: String?, _ expected: String) -> Bool {
        guard let actual else { return false }
        return actual.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            == expected.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    /// Returns a list of mismatches (empty == pass).
    static func exifMismatches(_ exif: ExifData?, _ expect: FormatManifest.Expect, tolerance: Double) -> [String] {
        guard let exif else { return ["no EXIF"] }
        var problems: [String] = []
        if !same(exif.cameraMake, expect.make) { problems.append("make \(exif.cameraMake ?? "nil") != \(expect.make)") }
        if !same(exif.cameraModel, expect.model) { problems.append("model \(exif.cameraModel ?? "nil") != \(expect.model)") }
        if exif.iso != expect.iso { problems.append("iso \(exif.iso.map { "\($0)" } ?? "nil") != \(expect.iso)") }
        if !close(exif.shutterSpeed, expect.exposure_time_s, tolerance) {
            problems.append("exposure \(exif.shutterSpeed.map { "\($0)" } ?? "nil") != \(expect.exposure_time_s)")
        }
        if !close(exif.aperture, expect.f_number, tolerance) {
            problems.append("f \(exif.aperture.map { "\($0)" } ?? "nil") != \(expect.f_number)")
        }
        if !close(exif.focalLength, expect.focal_length_mm, tolerance) {
            problems.append("focal \(exif.focalLength.map { "\($0)" } ?? "nil") != \(expect.focal_length_mm)")
        }
        if let lens = expect.lens, !same(exif.lens, lens) { problems.append("lens \(exif.lens) != \(lens)") }
        return problems
    }

    static func requiredDisplayEdge(_ expect: FormatManifest.Expect, manifest: FormatManifest) -> Int {
        min(manifest.display.min_long_edge_px, expect.long_edge_px)
    }
}

// MARK: - a. Contract (no corpus needed)

@Suite("Format conformance: contract")
struct FormatContractTests {
    let manifest = FormatManifest.shared

    @Test("Every contract extension is recognised, case-insensitively")
    func recognisesEveryExtension() {
        for format in manifest.formats {
            for ext in format.extensions {
                let variants = [ext.lowercased(), ext.uppercased(), ext.prefix(1).uppercased() + ext.dropFirst().lowercased()]
                for variant in variants {
                    let url = URL(fileURLWithPath: "/photos/IMG_0001.\(variant)")
                    #expect(SupportedFormats.isPhoto(url), "\(format.id): .\(variant) not recognised")
                    #expect(DirectoryScanner.isImage(url), "\(format.id): scanner ignores .\(variant)")
                }
            }
        }
    }

    @Test("RAW formats pair as RAW; bitmaps never do")
    func rawClassification() {
        for format in manifest.formats {
            for ext in format.extensions {
                let url = URL(fileURLWithPath: "/photos/IMG_0001.\(ext.uppercased())")
                let expected = format.kind == "raw"
                #expect(DirectoryScanner.isRAW(url) == expected, "\(format.id): .\(ext) RAW classification")
            }
        }
    }

    @Test("Product extension lists match the contract exactly")
    func noExtensionsBeyondContract() {
        let contractRaw = Set(manifest.formats.filter { $0.kind == "raw" }.flatMap(\.extensions))
        let contractBitmap = Set(manifest.formats.filter { $0.kind == "bitmap" }.flatMap(\.extensions))
        #expect(SupportedFormats.rawExtensions == contractRaw)
        #expect(SupportedFormats.bitmapExtensions == contractBitmap)
    }

    @Test("Every contract format has a real statistics display name")
    func statisticsDisplayNames() {
        for format in manifest.formats {
            for ext in format.extensions {
                let name = LibraryStatisticsEngine.canonicalFormatName(for: ext)
                let known = SupportedFormats.format(forExtension: ext)?.displayName
                #expect(known != nil && name == known, "\(format.id): .\(ext) has no display name (got \(name))")
            }
            // Synonymous extensions group together in the statistics.
            let normalized = Set(format.extensions.map { LibraryStatisticsEngine.normalizeExtension($0) })
            #expect(normalized.count == 1, "\(format.id): extensions \(format.extensions) do not group")
        }
    }

    @Test("RAW+JPEG grouping picks the RAW as primary for every RAW extension")
    func groupingPrimaryIsRAW() {
        for format in manifest.formats where format.kind == "raw" {
            for ext in format.extensions {
                let files = [
                    URL(fileURLWithPath: "/photos/DSC0001.JPG"),
                    URL(fileURLWithPath: "/photos/DSC0001.\(ext.uppercased())")
                ]
                let primaries = DirectoryScanner.groupPrimaryPhotos(in: files)
                #expect(primaries.count == 1, "\(format.id): RAW+JPEG not grouped")
                #expect(primaries.first?.pathExtension.lowercased() == ext, "\(format.id): RAW not primary")
            }
        }
    }
}

// MARK: - b. Committed bitmaps

@Suite("Format conformance: committed bitmaps")
struct FormatBitmapConformanceTests {
    static let sampleIDs = FormatManifest.shared.repoSamples.map(\.id)

    @Test("Discover, display and read EXIF", arguments: sampleIDs)
    func bitmapSample(_ id: String) async throws {
        let manifest = FormatManifest.shared
        let sample = manifest.samples.first { $0.id == id }!
        let source = FormatManifest.directory.appendingPathComponent(sample.file)

        let folder = try FormatProbe.tempFolder(id)
        defer { try? FileManager.default.removeItem(at: folder) }
        let target = folder.appendingPathComponent(source.lastPathComponent)
        try FileManager.default.copyItem(at: source, to: target)

        let items = try await FormatProbe.scan(folder)
        #expect(items.count == 1, "\(id): scanner found \(items.count) items")

        let image = await FormatProbe.displayImage(target)
        let edge = image.map { max($0.width, $0.height) } ?? 0
        #expect(edge >= FormatProbe.requiredDisplayEdge(sample.expect, manifest: manifest), "\(id): display long edge \(edge)")

        let exif = CullingWorkspaceViewModel.readMetadataWithCompanionFallback(for: target)
        let problems = FormatProbe.exifMismatches(exif, sample.expect, tolerance: manifest.exif_tolerance.relative)
        #expect(problems.isEmpty, "\(id): \(problems)")
    }
}

// MARK: - c. Real-camera RAW corpus

private let corpusAvailable = FormatManifest.shared.missingCorpusFiles.isEmpty

@Suite("Format conformance: RAW corpus")
struct FormatCorpusConformanceTests {
    static let sampleIDs = FormatManifest.shared.corpusSamples.map(\.id)

    @Test("Corpus present (required when PST_REQUIRE_FORMAT_CORPUS=1)")
    func corpusPresence() throws {
        let manifest = FormatManifest.shared
        let missing = manifest.missingCorpusFiles
        if manifest.corpusRequired {
            #expect(missing.isEmpty, "RAW corpus incomplete in \(manifest.corpusDirectory.path): \(missing). Run python3 \(manifest.corpus.fetch_script)")
        } else if !missing.isEmpty {
            print("SKIPPED: RAW format corpus missing in \(manifest.corpusDirectory.path) (\(missing.count) files). Run python3 \(manifest.corpus.fetch_script)")
        }
    }

    @Test(
        "Discover, pair, display, read EXIF and score",
        .enabled(if: corpusAvailable, "RAW corpus missing — run python3 scripts/fetch_format_corpus.py"),
        arguments: sampleIDs
    )
    func rawSample(_ id: String) async throws {
        let manifest = FormatManifest.shared
        let sample = manifest.samples.first { $0.id == id }!
        let source = manifest.corpusDirectory.appendingPathComponent(sample.file)
        let stem = source.deletingPathExtension().lastPathComponent
        let ext = source.pathExtension
        var results: [String: (Bool, String)] = [:]

        // Discovery: upper- and lower-case extension variants, each in its own folder
        // (APFS is case-insensitive). Symlinks keep the 20–30 MB files out of the temp dir.
        var discovered = true
        var discoveryNote = ""
        for variant in [ext.uppercased(), ext.lowercased()] {
            let folder = try FormatProbe.tempFolder("\(id)-\(variant)")
            defer { try? FileManager.default.removeItem(at: folder) }
            let link = folder.appendingPathComponent("\(stem).\(variant)")
            try FileManager.default.createSymbolicLink(at: link, withDestinationURL: source)
            let items = try await FormatProbe.scan(folder)
            if items.count != 1 {
                discovered = false
                discoveryNote += ".\(variant): \(items.count) items; "
            }
        }
        results["discover"] = (discovered, discoveryNote)

        // Pairing + display + EXIF + score happen in the paired folder, like a real SD card.
        let folder = try FormatProbe.tempFolder("\(id)-pair")
        defer { try? FileManager.default.removeItem(at: folder) }
        let raw = folder.appendingPathComponent(source.lastPathComponent)
        try FileManager.default.createSymbolicLink(at: raw, withDestinationURL: source)
        let jpeg = folder.appendingPathComponent("\(stem).JPG")
        try FileManager.default.copyItem(at: FormatManifest.directory.appendingPathComponent("bitmaps/sample.jpg"), to: jpeg)

        let items = try await FormatProbe.scan(folder)
        let primary = items.first?.url
        let companions = primary.map { FileCullingManager().findCompanionFiles(for: $0) } ?? []
        let paired = items.count == 1
            && primary?.lastPathComponent == raw.lastPathComponent
            && companions.contains { $0.lastPathComponent == jpeg.lastPathComponent }
        results["pair"] = (paired, "items=\(items.map(\.filename)) companions=\(companions.map(\.lastPathComponent))")

        let image = await FormatProbe.displayImage(raw)
        let edge = image.map { max($0.width, $0.height) } ?? 0
        let needed = FormatProbe.requiredDisplayEdge(sample.expect, manifest: manifest)
        results["display"] = (edge >= needed, "long edge \(edge) < \(needed)")

        // The RAW's own EXIF: read it alone so a companion JPEG cannot mask a reader gap.
        let exif = ImageMetadataReader.readMetadata(from: source)
        let problems = FormatProbe.exifMismatches(exif, sample.expect, tolerance: manifest.exif_tolerance.relative)
        results["exif"] = (problems.isEmpty, "\(problems)")

        var scored = false
        var scoreNote = "no display image to score"
        if let image {
            let scores = await QualityScoringService.score(cgImage: image)
            scored = scores.sharpness.isFinite && scores.sharpness > 0
            scoreNote = "sharpness \(scores.sharpness)"
        }
        results["score"] = (scored, scoreNote)

        let gaps = FormatKnownGaps.gaps[id] ?? [:]
        var row: [String] = []
        for capability in ["discover", "pair", "display", "exif", "score"] {
            let (passed, note) = results[capability]!
            row.append("\(capability)=\(passed ? "ok" : "FAIL")")
            if let reason = gaps[capability] {
                #expect(!passed, "\(id): known gap '\(capability)' (\(reason)) now passes — remove it from FormatKnownGaps")
            } else {
                #expect(passed, "\(id): \(capability) failed — \(note)")
            }
        }
        print("FORMAT-CONFORMANCE \(id): \(row.joined(separator: " "))")
    }
}
