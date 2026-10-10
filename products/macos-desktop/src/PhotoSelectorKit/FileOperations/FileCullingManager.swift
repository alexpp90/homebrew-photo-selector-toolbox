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

/// Manages culling file actions (Selection folder move/copy, system trash) and companion file discovery.
public final class FileCullingManager: @unchecked Sendable {

    private let fileManager: FileManager

    public init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    // MARK: - Companion File Discovery

    /// Finds all companion files associated with the specified primary photo file in the same directory.
    /// Includes RAW+JPEG pairs, XMP sidecars, and Lightroom edits (-Edit.*).
    /// - Parameter photoURL: The primary photo file URL.
    /// - Returns: An array of URLs including the primary photo and all discovered companions.
    public func findCompanionFiles(for photoURL: URL) -> [URL] {
        let canonicalPrimary = photoURL.standardizedFileURL.resolvingSymlinksInPath()
        let parentDir = canonicalPrimary.deletingLastPathComponent()
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
            let candidateCanonical = rawCandidate.standardizedFileURL.resolvingSymlinksInPath()
            if candidateCanonical == canonicalPrimary { continue }
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

            // Overwrite existing file at destination if present (collision between different files)
            if fileManager.fileExists(atPath: destination.path) {
                try? fileManager.removeItem(at: destination)
            }

            do {
                try fileManager.moveItem(at: target, to: destination)
                movedURLs.append(destination)
            } catch {
                throw FileCullingError.moveFailed(
                    source: target,
                    destination: destination,
                    reason: error.localizedDescription
                )
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

            // Overwrite existing file at destination if present (collision between different files)
            if fileManager.fileExists(atPath: destination.path) {
                try? fileManager.removeItem(at: destination)
            }

            do {
                try fileManager.copyItem(at: target, to: destination)
                copiedURLs.append(destination)
            } catch {
                throw FileCullingError.copyFailed(
                    source: target,
                    destination: destination,
                    reason: error.localizedDescription
                )
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

        // In headless CI environments (e.g. GitHub Actions), FileManager.trashItem blocks indefinitely
        // waiting for an interactive desktop user session / Finder. Fall back to moving to a dedicated
        // simulated trash directory so file operations and undo remain fully testable without hanging.
        let isCI = ProcessInfo.processInfo.environment["CI"] != nil
        if isCI {
            let simulatedTrash = fileManager.temporaryDirectory.appendingPathComponent(".SimulatedTrash", isDirectory: true)
            let batchDir = simulatedTrash.appendingPathComponent(UUID().uuidString, isDirectory: true)
            try? fileManager.createDirectory(at: batchDir, withIntermediateDirectories: true)
            for target in targets {
                guard fileManager.fileExists(atPath: target.path) else { continue }
                let destination = batchDir.appendingPathComponent(target.lastPathComponent)
                do {
                    try fileManager.moveItem(at: target, to: destination)
                    trashedURLs.append(destination)
                } catch {
                    throw FileCullingError.trashFailed(url: target, reason: error.localizedDescription)
                }
            }
            return trashedURLs
        }

        for target in targets {
            guard fileManager.fileExists(atPath: target.path) else { continue }
            var resultingURL: NSURL?
            do {
                try fileManager.trashItem(at: target, resultingItemURL: &resultingURL)
                if let res = resultingURL as URL? {
                    trashedURLs.append(res)
                } else {
                    trashedURLs.append(target)
                }
            } catch {
                throw FileCullingError.trashFailed(url: target, reason: error.localizedDescription)
            }
        }

        return trashedURLs
    }
}
