import Foundation

/// Action type representing a culling operation.
public enum CullingActionType: String, Sendable, Codable, CaseIterable {
    case move
    case copy
    case trash
}

/// Represents a culling job to be executed asynchronously on disk.
public struct CullingJob: Sendable, Identifiable {
    public let id: UUID
    public let photoID: UUID
    public let actionType: CullingActionType
    public let primaryURL: URL
    public let rootURL: URL?
    public let destinationFolderName: String
    public let timestamp: Date

    public init(
        id: UUID = UUID(),
        photoID: UUID,
        actionType: CullingActionType,
        primaryURL: URL,
        rootURL: URL?,
        destinationFolderName: String = AppSettingsDefaults.cullingDestinationFolderName,
        timestamp: Date = Date()
    ) {
        self.id = id
        self.photoID = photoID
        self.actionType = actionType
        self.primaryURL = primaryURL
        self.rootURL = rootURL
        self.destinationFolderName = destinationFolderName
        self.timestamp = timestamp
    }
}

/// Execution record capturing affected files for auditing, undo, and rollback.
public struct CullingExecutionRecord: Sendable, Identifiable {
    public let jobID: UUID
    public var id: UUID { jobID }
    public let photoID: UUID
    public let actionType: CullingActionType
    public let originalPrimaryURL: URL
    public let affectedURLs: [URL]
    public let previousStatus: PhotoStatus
    public let timestamp: Date

    public init(
        jobID: UUID,
        photoID: UUID,
        actionType: CullingActionType,
        originalPrimaryURL: URL,
        affectedURLs: [URL],
        previousStatus: PhotoStatus = .candidate,
        timestamp: Date = Date()
    ) {
        self.jobID = jobID
        self.photoID = photoID
        self.actionType = actionType
        self.originalPrimaryURL = originalPrimaryURL
        self.affectedURLs = affectedURLs
        self.previousStatus = previousStatus
        self.timestamp = timestamp
    }
}

/// Actor-isolated asynchronous executor providing a serial FIFO queue for non-blocking disk I/O.
/// Bridges between rapid UI interactions (< 1ms) and disk operations via FileCullingManager,
/// supporting in-flight job cancellation, undo reversals, and rollback support.
public actor AsyncCullingActor {

    private let cullingManager: FileCullingManager
    private let fileManager: FileManager
    private var cancelledJobIDs: Set<UUID> = []
    private var executedJobIDs: Set<UUID> = []
    private var completedRecords: [UUID: CullingExecutionRecord] = [:]

    public init(
        cullingManager: FileCullingManager = FileCullingManager(),
        fileManager: FileManager = .default
    ) {
        self.cullingManager = cullingManager
        self.fileManager = fileManager
    }

    /// Enqueues and serially executes a culling job.
    /// Returns the execution record upon completion, or nil if cancelled prior to execution.
    public func enqueue(_ job: CullingJob) async throws -> CullingExecutionRecord? {
        if cancelledJobIDs.contains(job.id) {
            return nil
        }

        if Task.isCancelled {
            cancelledJobIDs.insert(job.id)
            throw CancellationError()
        }

        let record = try executeJob(job)
        completedRecords[job.id] = record
        return record
    }

    /// Marks a job ID as cancelled. If the job hasn't started execution, it will be skipped.
    public func cancelJob(id: UUID) {
        cancelledJobIDs.insert(id)
    }

    /// Cancels a pending job or reverses a completed/in-flight culling operation.
    /// If the job completed on disk, uses the actor's cached completed record to restore files.
    /// If the job was cancelled prior to disk I/O, records cancellation and returns an empty array.
    /// If a fallback record contains affected files, reverses them.
    /// - Parameters:
    ///   - jobID: The unique identifier of the job to cancel or undo.
    ///   - fallbackRecord: The record available at the caller site (e.g. optimistic or cached record).
    /// - Returns: The list of restored URLs.
    public func cancelOrUndo(jobID: UUID, fallbackRecord: CullingExecutionRecord) async throws -> [URL] {
        cancelledJobIDs.insert(jobID)
        if let completedRecord = completedRecords[jobID] {
            completedRecords.removeValue(forKey: jobID)
            return try await undo(record: completedRecord)
        } else if !fallbackRecord.affectedURLs.isEmpty {
            return try await undo(record: fallbackRecord)
        }
        return []
    }

    /// Reverses a previously executed culling operation.
    /// - Parameter record: The execution record to undo.
    /// - Returns: The list of restored URLs.
    public func undo(record: CullingExecutionRecord) async throws -> [URL] {
        guard !record.affectedURLs.isEmpty else {
            return []
        }

        // If job was cancelled before execution completed, undo is a clean no-op
        if cancelledJobIDs.contains(record.jobID) && !executedJobIDs.contains(record.jobID) {
            return []
        }

        var restoredURLs: [URL] = []
        let originalDir = record.originalPrimaryURL.deletingLastPathComponent()

        switch record.actionType {
        case .move:
            // Move files back from Selection/ to original directory
            for movedURL in record.affectedURLs {
                let filename = movedURL.lastPathComponent
                let destination = originalDir.appendingPathComponent(filename)

                let canonicalMoved = movedURL.standardizedFileURL.resolvingSymlinksInPath()
                let canonicalDest = destination.standardizedFileURL.resolvingSymlinksInPath()

                // Guard against self-move: if movedURL already resides at destination,
                // it was never moved away (e.g. in-flight cancellation or optimistic record)
                guard movedURL.standardizedFileURL.path != destination.standardizedFileURL.path,
                      canonicalMoved.path != canonicalDest.path else {
                    continue
                }

                if fileManager.fileExists(atPath: movedURL.path) {
                    if fileManager.fileExists(atPath: destination.path) {
                        try? fileManager.removeItem(at: destination)
                    }
                    try fileManager.moveItem(at: movedURL, to: destination)
                    restoredURLs.append(destination)
                }
            }

        case .copy:
            // Remove copied files in Selection/
            for copiedURL in record.affectedURLs {
                let canonicalCopied = copiedURL.standardizedFileURL.resolvingSymlinksInPath()
                let canonicalOriginal = record.originalPrimaryURL.standardizedFileURL.resolvingSymlinksInPath()

                // Strict guard: NEVER delete the original source file on disk!
                guard copiedURL.standardizedFileURL.path != record.originalPrimaryURL.standardizedFileURL.path,
                      canonicalCopied.path != canonicalOriginal.path else {
                    continue
                }

                if fileManager.fileExists(atPath: copiedURL.path) {
                    try fileManager.removeItem(at: copiedURL)
                    restoredURLs.append(copiedURL)
                }
            }

        case .trash:
            // Move files back from Trash if they exist at their trashed location
            for (index, trashedURL) in record.affectedURLs.enumerated() {
                let destination: URL
                if index == 0 {
                    destination = record.originalPrimaryURL
                } else {
                    let filename = cleanTrashedFilename(
                        trashedURL.lastPathComponent,
                        originalPrimaryURL: record.originalPrimaryURL
                    )
                    destination = originalDir.appendingPathComponent(filename)
                }

                let canonicalTrashed = trashedURL.standardizedFileURL.resolvingSymlinksInPath()
                let canonicalDest = destination.standardizedFileURL.resolvingSymlinksInPath()

                guard trashedURL.standardizedFileURL.path != destination.standardizedFileURL.path,
                      canonicalTrashed.path != canonicalDest.path else {
                    continue
                }

                if fileManager.fileExists(atPath: trashedURL.path) {
                    if fileManager.fileExists(atPath: destination.path) {
                        try? fileManager.removeItem(at: destination)
                    }
                    try fileManager.moveItem(at: trashedURL, to: destination)
                    restoredURLs.append(destination)
                }
            }
        }

        executedJobIDs.remove(record.jobID)
        cancelledJobIDs.remove(record.jobID)
        completedRecords.removeValue(forKey: record.jobID)
        return restoredURLs
    }

    /// Returns true if the actor currently holds a completed execution record for the given job ID.
    public func hasCompletedRecord(for jobID: UUID) -> Bool {
        completedRecords[jobID] != nil
    }

    // MARK: - Private Execution

    private func executeJob(_ job: CullingJob) throws -> CullingExecutionRecord {
        guard !cancelledJobIDs.contains(job.id) else {
            throw CancellationError()
        }

        let affectedURLs: [URL]
        switch job.actionType {
        case .move:
            guard let root = job.rootURL else {
                throw FileCullingError.directoryCreationFailed(job.primaryURL, "Missing root directory")
            }
            affectedURLs = try cullingManager.moveToSelection(
                photoURL: job.primaryURL,
                rootURL: root,
                destinationFolderName: job.destinationFolderName
            )

        case .copy:
            guard let root = job.rootURL else {
                throw FileCullingError.directoryCreationFailed(job.primaryURL, "Missing root directory")
            }
            affectedURLs = try cullingManager.copyToSelection(
                photoURL: job.primaryURL,
                rootURL: root,
                destinationFolderName: job.destinationFolderName
            )

        case .trash:
            affectedURLs = try cullingManager.moveToTrash(photoURL: job.primaryURL)
        }

        executedJobIDs.insert(job.id)

        return CullingExecutionRecord(
            jobID: job.id,
            photoID: job.photoID,
            actionType: job.actionType,
            originalPrimaryURL: job.primaryURL,
            affectedURLs: affectedURLs,
            previousStatus: .candidate,
            timestamp: Date()
        )
    }

    private func cleanTrashedFilename(_ trashedFilename: String, originalPrimaryURL: URL) -> String {
        let primaryFilename = originalPrimaryURL.lastPathComponent
        if trashedFilename == primaryFilename {
            return primaryFilename
        }

        let ext = (trashedFilename as NSString).pathExtension
        let stem = (trashedFilename as NSString).deletingPathExtension

        let cleanedStem = stem.replacingOccurrences(
            of: #"\s+(\d{2}-\d{2}-\d{2}-\d{3}|\d+)$"#,
            with: "",
            options: .regularExpression
        )

        if cleanedStem.lowercased().hasSuffix(".\(ext.lowercased())") {
            return cleanedStem
        }

        if !ext.isEmpty {
            return "\(cleanedStem).\(ext)"
        }
        return cleanedStem
    }
}
