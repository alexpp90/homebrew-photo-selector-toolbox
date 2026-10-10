import Foundation

/// Protocol defining asynchronous folder scanning operations.
public protocol DirectoryScanning: Sendable {
    /// Streams discovered photos progressively.
    ///
    /// Contract: emissions are **append-only deltas** (each batch contains only new items, never
    /// repeats), ordered naturally by filename within each folder, folders depth-first in natural
    /// order. The first batch holds at most `firstBatchSize` items so a UI can show the first
    /// photograph after a single directory listing; later batches ramp up to `batchSize`.
    func scanStream(
        at directoryURL: URL,
        recursive: Bool,
        batchSize: Int,
        firstBatchSize: Int
    ) -> AsyncStream<[PhotoItem]>

    /// Settled result: every photo in the folder, sorted naturally by filename.
    func scanDirectory(
        at directoryURL: URL,
        recursive: Bool
    ) async throws -> [PhotoItem]
}

public extension DirectoryScanning {
    /// Constant-size batches (`firstBatchSize == batchSize`).
    func scanStream(
        at directoryURL: URL,
        recursive: Bool,
        batchSize: Int
    ) -> AsyncStream<[PhotoItem]> {
        scanStream(at: directoryURL, recursive: recursive, batchSize: batchSize, firstBatchSize: batchSize)
    }
}

/// Fast asynchronous directory scanner for SD cards (DCIM structures) and local photo folders.
///
/// Performance model: every directory is listed **exactly once** and companion grouping
/// (RAW+JPEG, `.xmp` sidecars, `<stem>-Edit.*`) is computed in memory from that listing.
/// The walk performs no per-file symlink resolution and reads no EXIF — metadata is resolved
/// lazily by the workspace after the first photographs are on screen. The previous design
/// listed the parent directory once *per file* (O(N²) syscalls), which made large SD cards
/// take tens of seconds before the first image appeared.
public final class DirectoryScanner: DirectoryScanning, @unchecked Sendable {

    public static let shared = DirectoryScanner()

    public static let rawExtensions: Set<String> = [
        "cr2", "cr3", "nef", "arw", "dng", "raf", "rw2", "orf", "pef", "3fr", "raw"
    ]

    public static let standardExtensions: Set<String> = [
        "jpg", "jpeg", "heic", "heif", "tif", "tiff", "png"
    ]

    public static let sidecarExtensions: Set<String> = [
        "xmp"
    ]

    /// Folder names (case-insensitive) that hold already-culled output and are never descended
    /// into, unless the folder itself is the scan root (REQ-MAC-EXIF.06).
    public static let builtInExcludedFolderNames: Set<String> = [
        "selection", "selected", "phototok_selection", "phototok_leftswipe"
    ]

    private let fileManager: FileManager
    private let userDefaults: UserDefaults

    public init(fileManager: FileManager = .default, userDefaults: UserDefaults = .standard) {
        self.fileManager = fileManager
        self.userDefaults = userDefaults
    }

    /// Effective excluded folder names: built-ins plus the user's custom destination folder.
    public func excludedFolderNames() -> Set<String> {
        var names = Self.builtInExcludedFolderNames
        let custom = userDefaults.cullingDestinationFolderName.trimmingCharacters(in: .whitespacesAndNewlines)
        if !custom.isEmpty {
            names.insert(custom.lowercased())
        }
        return names
    }

    /// Progressively streams discovered photo items in batches.
    /// - Parameters:
    ///   - directoryURL: The root directory to scan.
    ///   - recursive: Whether to recurse into subdirectories.
    ///   - batchSize: Steady-state batch size.
    ///   - firstBatchSize: Size of the very first batch (use 1 to show the first photo ASAP).
    public func scanStream(
        at directoryURL: URL,
        recursive: Bool = true,
        batchSize: Int = 30,
        firstBatchSize: Int
    ) -> AsyncStream<[PhotoItem]> {
        let excluded = excludedFolderNames()
        return AsyncStream { continuation in
            let task = Task.detached(priority: .userInitiated) { [self, excluded] in
                var batcher = ScanBatcher(firstBatchSize: firstBatchSize, batchSize: batchSize)
                let root = directoryURL.standardizedFileURL.resolvingSymlinksInPath()

                // Iterative depth-first walk: files of a folder first, then its subfolders in
                // natural order. A stack (not recursion) keeps deep DCIM trees safe.
                var pending: [URL] = [root]
                while let directory = pending.popLast() {
                    if Task.isCancelled { break }

                    let listing = Self.list(directory: directory, fileManager: self.fileManager)
                    let primaries = Self.groupPrimaryPhotos(in: listing.files)

                    for primary in primaries {
                        if Task.isCancelled { break }
                        if let ready = batcher.append(PhotoItem(url: primary, status: .candidate)) {
                            continuation.yield(ready)
                        }
                    }
                    // Never hold items back across a (potentially slow) directory listing.
                    if let ready = batcher.flush() {
                        continuation.yield(ready)
                    }

                    if recursive {
                        let subdirectories = listing.directories
                            .filter { !excluded.contains($0.lastPathComponent.lowercased()) }
                            .sorted { Self.naturalOrder($0.lastPathComponent, $1.lastPathComponent) }
                        // Push in reverse so the naturally-first subfolder is visited first.
                        pending.append(contentsOf: subdirectories.reversed())
                    }
                }

                if !Task.isCancelled, let ready = batcher.flush() {
                    continuation.yield(ready)
                }
                continuation.finish()
            }

            continuation.onTermination = { @Sendable _ in
                task.cancel()
            }
        }
    }

    /// Asynchronously scans the directory and returns all photo candidates,
    /// sorted naturally by filename (e.g. IMG_2 before IMG_10).
    public func scanDirectory(
        at directoryURL: URL,
        recursive: Bool = true
    ) async throws -> [PhotoItem] {
        var allItems: [PhotoItem] = []
        for await batch in scanStream(at: directoryURL, recursive: recursive, batchSize: 50, firstBatchSize: 50) {
            try Task.checkCancellation()
            allItems.append(contentsOf: batch)
        }

        return allItems.sorted {
            $0.filename.localizedStandardCompare($1.filename) == .orderedAscending
        }
    }

    // MARK: - Directory listing

    struct DirectoryListing {
        var files: [URL] = []
        var directories: [URL] = []
    }

    /// Lists one directory with prefetched resource values (one syscall batch per folder).
    static func list(directory: URL, fileManager: FileManager) -> DirectoryListing {
        let keys: [URLResourceKey] = [.isDirectoryKey, .isRegularFileKey, .isSymbolicLinkKey]
        guard let entries = try? fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: keys,
            options: [.skipsHiddenFiles]
        ) else {
            return DirectoryListing() // Unreadable folder: skip, never fatal.
        }

        var listing = DirectoryListing()
        listing.files.reserveCapacity(entries.count)
        for entry in entries {
            guard let values = try? entry.resourceValues(forKeys: Set(keys)) else { continue }
            if values.isDirectory == true {
                listing.directories.append(entry)
            } else if values.isRegularFile == true {
                listing.files.append(entry)
            } else if values.isSymbolicLink == true {
                // Rare: a symlinked file. Resolve once; symlinked folders are not followed
                // (avoids cycles, matches FileManager.enumerator semantics).
                let resolved = entry.resolvingSymlinksInPath()
                if (try? resolved.resourceValues(forKeys: [.isRegularFileKey]))?.isRegularFile == true {
                    listing.files.append(entry)
                }
            }
        }
        return listing
    }

    // MARK: - Companion grouping (pure, in-memory)

    /// Groups the files of **one directory** into photographs and returns the primary URL of
    /// each, in natural filename order.
    ///
    /// - Same stem, different extension (RAW+JPEG, `.xmp`) form one photograph.
    /// - `<stem>-Edit*` / `<stem>_edit*` derivatives join `<stem>` when that capture exists.
    /// - Primary preference: original capture over derivative, RAW over rendered, then name.
    /// - Sidecars alone (orphan `.xmp`) never produce a photograph.
    public static func groupPrimaryPhotos(in files: [URL]) -> [URL] {
        let images = files.filter { isImage($0) }
        guard !images.isEmpty else { return [] }

        let captureStems = Set(images.map { lowerStem(of: $0) })

        var groups: [String: [URL]] = [:]
        for image in images {
            let stem = lowerStem(of: image)
            let key = editBaseStem(of: stem).flatMap { captureStems.contains($0) ? $0 : nil } ?? stem
            groups[key, default: []].append(image)
        }

        let primaries: [URL] = groups.map { key, members in
            members.min { lhs, rhs in
                let lhsRank = primaryRank(lhs, groupKey: key)
                let rhsRank = primaryRank(rhs, groupKey: key)
                if lhsRank != rhsRank { return lhsRank < rhsRank }
                return lhs.lastPathComponent < rhs.lastPathComponent
            }!
        }

        return primaries.sorted { naturalOrder($0.lastPathComponent, $1.lastPathComponent) }
    }

    static func isImage(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        return rawExtensions.contains(ext) || standardExtensions.contains(ext)
    }

    static func isRAW(_ url: URL) -> Bool {
        rawExtensions.contains(url.pathExtension.lowercased())
    }

    static func lowerStem(of url: URL) -> String {
        url.deletingPathExtension().lastPathComponent.lowercased()
    }

    /// `"dsc0001-edit-2"` → `"dsc0001"`; `nil` when the stem is not a Lightroom derivative.
    static func editBaseStem(of lowerStem: String) -> String? {
        let markers = ["-edit", "_edit"]
        let ranges = markers.compactMap { lowerStem.range(of: $0) }
        guard let first = ranges.min(by: { $0.lowerBound < $1.lowerBound }) else { return nil }
        let base = String(lowerStem[..<first.lowerBound])
        return base.isEmpty ? nil : base
    }

    /// Lower is better: 0 = original RAW, 1 = original rendered, 2 = derivative RAW, 3 = derivative.
    static func primaryRank(_ url: URL, groupKey: String) -> Int {
        let isDerivative = lowerStem(of: url) != groupKey
        return (isDerivative ? 2 : 0) + (isRAW(url) ? 0 : 1)
    }

    static func naturalOrder(_ lhs: String, _ rhs: String) -> Bool {
        lhs.localizedStandardCompare(rhs) == .orderedAscending
    }
}

/// Batch sizing policy for progressive emission: a tiny first batch, then a quick ramp
/// (×4) up to the steady-state size. Pure value type so the policy is unit-testable.
public struct ScanBatcher: Sendable {
    public let batchSize: Int
    private(set) var currentLimit: Int
    private var buffer: [PhotoItem] = []

    public init(firstBatchSize: Int, batchSize: Int) {
        self.batchSize = max(1, batchSize)
        self.currentLimit = max(1, min(firstBatchSize, self.batchSize))
    }

    /// Appends an item; returns a full batch when the current limit is reached.
    public mutating func append(_ item: PhotoItem) -> [PhotoItem]? {
        buffer.append(item)
        guard buffer.count >= currentLimit else { return nil }
        return emit()
    }

    /// Emits whatever is buffered (or `nil` when empty).
    public mutating func flush() -> [PhotoItem]? {
        buffer.isEmpty ? nil : emit()
    }

    private mutating func emit() -> [PhotoItem] {
        let out = buffer
        buffer.removeAll(keepingCapacity: true)
        currentLimit = min(batchSize, currentLimit * 4)
        return out
    }
}
