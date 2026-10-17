import Foundation

/// Errors encountered during file culling operations.
public enum FileCullingError: Error, LocalizedError, Sendable {
    case fileNotFound(URL)
    case directoryCreationFailed(URL, String)
    case moveFailed(source: URL, destination: URL, reason: String)
    case copyFailed(source: URL, destination: URL, reason: String)
    case trashFailed(url: URL, reason: String)

    public var errorDescription: String? {
        switch self {
        case .fileNotFound(let url):
            return "File not found at path: \(url.path)"
        case .directoryCreationFailed(let url, let reason):
            return "Failed to create directory at \(url.path): \(reason)"
        case .moveFailed(let src, let dst, let reason):
            return "Failed to move file from \(src.lastPathComponent) to \(dst.lastPathComponent): \(reason)"
        case .copyFailed(let src, let dst, let reason):
            return "Failed to copy file from \(src.lastPathComponent) to \(dst.lastPathComponent): \(reason)"
        case .trashFailed(let url, let reason):
            return "Failed to move file to trash \(url.lastPathComponent): \(reason)"
        }
    }
}

/// Abstraction for moving a file to the trash, enabling deterministic test injection without
/// environment-variable branches in production code.
public protocol TrashService: Sendable {
    func trash(_ url: URL) throws -> URL
}

/// Production trash implementation backed by `FileManager.default.trashItem`.
public struct SystemTrashService: TrashService {
    public init() {}

    public func trash(_ url: URL) throws -> URL {
        var resultingURL: NSURL?
        do {
            try FileManager.default.trashItem(at: url, resultingItemURL: &resultingURL)
        } catch {
            throw FileCullingError.trashFailed(url: url, reason: error.localizedDescription)
        }
        guard let trashedURL = resultingURL as URL? else {
            throw FileCullingError.trashFailed(
                url: url,
                reason: "System trash did not return a resulting item URL."
            )
        }
        return trashedURL
    }
}

/// Manages culling file actions (Selection folder move/copy, system trash) and companion file discovery.
public final class FileCullingManager: @unchecked Sendable {

    private let fileManager: FileManager
    public let trashService: any TrashService

    public init(
        fileManager: FileManager = .default,
        trashService: any TrashService = SystemTrashService()
    ) {
        self.fileManager = fileManager
        self.trashService = trashService
    }

    // MARK: - Companion File Discovery

    /// Finds all companion files associated with the specified primary photo file in the same directory.
    /// Includes RAW+JPEG pairs, XMP sidecars, and Lightroom edits (-Edit.*).
    /// - Parameter photoURL: The primary photo file URL.
    /// - Returns: An array of URLs including the primary photo and all discovered companions.
    public func findCompanionFiles(for photoURL: URL) -> [URL] {
        // Resolve the *folder* only: a symlinked photo's companions are its neighbours in the
        // folder being culled, not the files next to the link target.
        let parentDir = photoURL.standardizedFileURL.deletingLastPathComponent().resolvingSymlinksInPath()
        let canonicalPrimary = parentDir.appendingPathComponent(photoURL.lastPathComponent)
        guard let contents = try? fileManager.contentsOfDirectory(
            at: parentDir,
            includingPropertiesForKeys: nil,
            options: [.skipsHiddenFiles]
        ) else {
            return [photoURL]
        }

        let primaryFilename = canonicalPrimary.lastPathComponent
        let primaryStem = canonicalPrimary.deletingPathExtension().lastPathComponent
        let stemLower = primaryStem.lowercased()
        let editPrefix = stemLower + "-edit"
        let editUnderscore = stemLower + "_edit"
        let dotXmp = primaryFilename.lowercased() + ".xmp"

        var companions = Set<URL>()
        companions.insert(photoURL)

        let baseDir = photoURL.deletingLastPathComponent()

        for rawCandidate in contents {
            if rawCandidate.lastPathComponent == canonicalPrimary.lastPathComponent { continue }
            let candidateName = rawCandidate.lastPathComponent
            let candidateNameLower = candidateName.lowercased()
            let candidateStemLower = rawCandidate.deletingPathExtension().lastPathComponent.lowercased()

            let candidateURL = baseDir.appendingPathComponent(candidateName)

            // 1. Same stem, different extension (e.g. DSC0001.ARW -> DSC0001.JPG, DSC0001.xmp)
            if candidateStemLower == stemLower {
                companions.insert(candidateURL)
                continue
            }

            // 2. Full filename + .xmp sidecar (e.g. DSC0001.ARW.xmp)
            if candidateNameLower == dotXmp {
                companions.insert(candidateURL)
                continue
            }

            // 3. Lightroom edits (e.g. DSC0001-Edit.tif, DSC0001-Edit.xmp)
            if candidateStemLower.hasPrefix(editPrefix) || candidateStemLower.hasPrefix(editUnderscore) {
                companions.insert(candidateURL)
                continue
            }
        }

        // Return primary first, then companions sorted alphabetically
        let others = companions.filter { $0 != photoURL }.sorted { $0.lastPathComponent < $1.lastPathComponent }
        return [photoURL] + others
    }

    // MARK: - Move to Selection

    /// Moves the primary photo and all associated companion files to `<rootURL>/Selection/`.
    /// - Parameters:
    ///   - photoURL: The primary photo URL to move.
    ///   - rootURL: The root album / SD card directory.
    /// - Returns: The list of destination file URLs moved into the Selection folder.
    public func moveToSelection(
        photoURL: URL,
        rootURL: URL,
        destinationFolderName: String = AppSettingsDefaults.cullingDestinationFolderName
    ) throws -> [URL] {
        guard fileManager.fileExists(atPath: photoURL.path) else {
            throw FileCullingError.fileNotFound(photoURL)
        }

        let folder = AppSettings.sanitizeDestinationFolderName(destinationFolderName)
        let selectionDir = rootURL.appendingPathComponent(folder, isDirectory: true)
        if !fileManager.fileExists(atPath: selectionDir.path) {
            do {
                try fileManager.createDirectory(at: selectionDir, withIntermediateDirectories: true)
            } catch {
                throw FileCullingError.directoryCreationFailed(selectionDir, error.localizedDescription)
            }
        }

        let targets = findCompanionFiles(for: photoURL)
        var movedURLs: [URL] = []
        var movedPairs: [(source: URL, destination: URL, backup: URL?)] = []

        for target in targets {
            guard fileManager.fileExists(atPath: target.path) else { continue }
            let destination = selectionDir.appendingPathComponent(target.lastPathComponent)

            // Guard against self-move: If target already resides at destination,
            // preserve the file without deletion or redundant filesystem move.
            let canonicalTarget = target.standardizedFileURL.resolvingSymlinksInPath()
            let canonicalDestination = destination.standardizedFileURL.resolvingSymlinksInPath()
            let isSameFile = (target.standardizedFileURL.path == destination.standardizedFileURL.path)
                || (canonicalTarget.path.caseInsensitiveCompare(canonicalDestination.path) == .orderedSame)

            if isSameFile {
                movedURLs.append(destination)
                continue
            }

            // Stage existing file at destination to a temporary backup rather than deleting before move
            var backupURL: URL? = nil
            if fileManager.fileExists(atPath: destination.path) {
                let candidateBackup = selectionDir.appendingPathComponent(".\(destination.lastPathComponent).bak.\(UUID().uuidString)")
                try? fileManager.moveItem(at: destination, to: candidateBackup)
                if fileManager.fileExists(atPath: candidateBackup.path) {
                    backupURL = candidateBackup
                } else {
                    try? fileManager.removeItem(at: destination)
                }
            }

            do {
                try fileManager.moveItem(at: target, to: destination)
                movedURLs.append(destination)
                movedPairs.append((source: target, destination: destination, backup: backupURL))
            } catch {
                // Restore current backup if present
                if let bak = backupURL, fileManager.fileExists(atPath: bak.path) {
                    try? fileManager.moveItem(at: bak, to: destination)
                }
                // Roll back all previously moved companion files in this transaction
                for pair in movedPairs.reversed() {
                    if fileManager.fileExists(atPath: pair.destination.path) {
                        try? fileManager.moveItem(at: pair.destination, to: pair.source)
                    }
                    if let bak = pair.backup, fileManager.fileExists(atPath: bak.path) {
                        try? fileManager.moveItem(at: bak, to: pair.destination)
                    }
                }
                throw FileCullingError.moveFailed(
                    source: target,
                    destination: destination,
                    reason: error.localizedDescription
                )
            }
        }

        for pair in movedPairs {
            if let bak = pair.backup {
                try? fileManager.removeItem(at: bak)
            }
        }

        return movedURLs
    }

    // MARK: - Copy to Selection

    /// Copies the primary photo and all associated companion files to `<rootURL>/Selection/`.
    /// - Parameters:
    ///   - photoURL: The primary photo URL to copy.
    ///   - rootURL: The root album / SD card directory.
    /// - Returns: The list of destination file URLs copied into the Selection folder.
    public func copyToSelection(
        photoURL: URL,
        rootURL: URL,
        destinationFolderName: String = AppSettingsDefaults.cullingDestinationFolderName
    ) throws -> [URL] {
        guard fileManager.fileExists(atPath: photoURL.path) else {
            throw FileCullingError.fileNotFound(photoURL)
        }

        let folder = AppSettings.sanitizeDestinationFolderName(destinationFolderName)
        let selectionDir = rootURL.appendingPathComponent(folder, isDirectory: true)
        if !fileManager.fileExists(atPath: selectionDir.path) {
            do {
                try fileManager.createDirectory(at: selectionDir, withIntermediateDirectories: true)
            } catch {
                throw FileCullingError.directoryCreationFailed(selectionDir, error.localizedDescription)
            }
        }

        let targets = findCompanionFiles(for: photoURL)
        var copiedURLs: [URL] = []
        var copiedEntries: [(destination: URL, backup: URL?)] = []

        for target in targets {
            guard fileManager.fileExists(atPath: target.path) else { continue }
            let destination = selectionDir.appendingPathComponent(target.lastPathComponent)

            // Guard against self-copy: If target already resides at destination,
            // preserve the file without deletion or redundant filesystem copy.
            let canonicalTarget = target.standardizedFileURL.resolvingSymlinksInPath()
            let canonicalDestination = destination.standardizedFileURL.resolvingSymlinksInPath()
            let isSameFile = (target.standardizedFileURL.path == destination.standardizedFileURL.path)
                || (canonicalTarget.path.caseInsensitiveCompare(canonicalDestination.path) == .orderedSame)

            if isSameFile {
                copiedURLs.append(destination)
                continue
            }

            // Stage existing file at destination to a temporary backup before copy
            var backupURL: URL? = nil
            if fileManager.fileExists(atPath: destination.path) {
                let candidateBackup = selectionDir.appendingPathComponent(".\(destination.lastPathComponent).bak.\(UUID().uuidString)")
                try? fileManager.moveItem(at: destination, to: candidateBackup)
                if fileManager.fileExists(atPath: candidateBackup.path) {
                    backupURL = candidateBackup
                } else {
                    try? fileManager.removeItem(at: destination)
                }
            }

            do {
                try fileManager.copyItem(at: target, to: destination)
                copiedURLs.append(destination)
                copiedEntries.append((destination: destination, backup: backupURL))
            } catch {
                if fileManager.fileExists(atPath: destination.path) {
                    try? fileManager.removeItem(at: destination)
                }
                if let bak = backupURL, fileManager.fileExists(atPath: bak.path) {
                    try? fileManager.moveItem(at: bak, to: destination)
                }
                for entry in copiedEntries.reversed() {
                    if fileManager.fileExists(atPath: entry.destination.path) {
                        try? fileManager.removeItem(at: entry.destination)
                    }
                    if let bak = entry.backup, fileManager.fileExists(atPath: bak.path) {
                        try? fileManager.moveItem(at: bak, to: entry.destination)
                    }
                }
                throw FileCullingError.copyFailed(
                    source: target,
                    destination: destination,
                    reason: error.localizedDescription
                )
            }
        }

        for entry in copiedEntries {
            if let bak = entry.backup {
                try? fileManager.removeItem(at: bak)
            }
        }

        return copiedURLs
    }

    // MARK: - Move to Trash

    /// Sends the primary photo and all associated companion files to the macOS system trash.
    /// - Parameter photoURL: The primary photo URL to trash.
    /// - Returns: The list of resulting URLs in the trash.
    public func moveToTrash(photoURL: URL) throws -> [URL] {
        guard fileManager.fileExists(atPath: photoURL.path) else {
            throw FileCullingError.fileNotFound(photoURL)
        }

        let targets = findCompanionFiles(for: photoURL)
        var trashedURLs: [URL] = []
        var trashedPairs: [(source: URL, trashed: URL)] = []

        for target in targets {
            guard fileManager.fileExists(atPath: target.path) else { continue }
            do {
                let res = try trashService.trash(target)
                trashedURLs.append(res)
                trashedPairs.append((source: target, trashed: res))
            } catch {
                for pair in trashedPairs.reversed() {
                    if fileManager.fileExists(atPath: pair.trashed.path) {
                        try? fileManager.moveItem(at: pair.trashed, to: pair.source)
                    }
                }
                if let cullingError = error as? FileCullingError {
                    throw cullingError
                }
                throw FileCullingError.trashFailed(url: target, reason: error.localizedDescription)
            }
        }

        return trashedURLs
    }
}
