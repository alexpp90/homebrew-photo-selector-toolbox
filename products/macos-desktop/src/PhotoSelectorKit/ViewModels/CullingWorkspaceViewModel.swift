import SwiftUI
import AppKit
import Foundation

/// Layout mode for the culling workspace preview canvas.
public enum ComparisonMode: Int, CaseIterable, Identifiable, Sendable {
    case single = 1
    case sideBySide = 2
    case triplet = 3

    public var id: Int { rawValue }

    public var title: String {
        switch self {
        case .single: return "1-Up"
        case .sideBySide: return "2-Up"
        case .triplet: return "3-Up"
        }
    }

    public var systemImage: String {
        switch self {
        case .single: return "rectangle.fill"
        case .sideBySide: return "rectangle.split.2x1.fill"
        case .triplet: return "rectangle.split.1x2.fill"
        }
    }
}

/// Zoom presentation state for canvas previews.
public enum ZoomState: Sendable, Equatable {
    /// Whole photograph fitted to its viewport.
    case fit
    /// True 100 %: one image pixel per screen pixel (computed from the original dimensions).
    case oneToOne
    /// Free trackpad pinch magnification, relative to Fit (always > 1).
    case magnified(Double)

    public var isZoomed: Bool { self != .fit }

    /// Pinch magnifications at or below this factor snap back to Fit.
    public static let snapToFitThreshold: Double = 1.02
}

/// Transient feedback toast notification message.
public struct ToastMessage: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let icon: String
    public let title: String
    public let subtitle: String?
    public let actionType: CullingActionType?

    public init(
        id: UUID = UUID(),
        icon: String,
        title: String,
        subtitle: String? = nil,
        actionType: CullingActionType? = nil
    ) {
        self.id = id
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.actionType = actionType
    }
}

/// MainActor state machine managing photos, navigation, comparison slots,
/// zoom/pan sync, optimistic status mutations (<1ms), auto-advance, and keyboard routing.
@MainActor
public final class CullingWorkspaceViewModel: ObservableObject {

    // MARK: - Published State
    @Published public private(set) var photos: [PhotoItem] = []
    @Published public var currentIndex: Int = 0 {
        didSet {
            syncSlotsToCurrentIndex()
            prefetchAroundCurrentIndex()
        }
    }
    @Published public var comparisonSlotIndices: [Int] = [0]
    @Published public var activeSlotIndex: Int = 0
    @Published public var comparisonMode: ComparisonMode = .single {
        didSet {
            setupSlotsForMode(comparisonMode)
        }
    }
    @Published public var zoomState: ZoomState = .fit
    @Published public var isZoomSynced: Bool = true
    @Published public var isFilmstripVisible: Bool = true
    @Published public var isInfoHUDVisible: Bool = true
    @Published public var autoAdvance: Bool = true
    @Published public var isDropTargeted: Bool = false
    @Published public var currentToast: ToastMessage?
    @Published public private(set) var rootDirectoryURL: URL?
    @Published public private(set) var isLoading: Bool = false
    @Published public var isDuplicateFinderPresented: Bool = false
    @Published public var isLibraryStatisticsPresented: Bool = false
    @Published public var isShortcutsHelpPresented: Bool = false

    public func presentDuplicateFinder() {
        isDuplicateFinderPresented = true
    }

    public func presentLibraryStatistics() {
        isLibraryStatisticsPresented = true
    }

    public func presentShortcutsHelp() {
        isShortcutsHelpPresented = true
    }

    public var selectedCount: Int {
        photos.filter { $0.status == .selected }.count
    }

    public var trashedCount: Int {
        photos.filter { $0.status == .trashed }.count
    }

    public var canUndo: Bool {
        !undoStack.isEmpty
    }

    /// Synchronizes file deletion from secondary tools into the active culling candidate list.
    public func markPhotoAsTrashed(url: URL) {
        let canonical = url.standardizedFileURL.resolvingSymlinksInPath()
        let normPath = Self.normalizedPathString(url)
        if let index = photos.firstIndex(where: {
            $0.url == url
                || $0.url.standardizedFileURL.resolvingSymlinksInPath() == canonical
                || Self.normalizedPathString($0.url) == normPath
        }) {
            photos[index].status = .trashed
        }
    }

    private static func normalizedPathString(_ u: URL) -> String {
        let p = u.path
        if p.hasPrefix("/private/") {
            return String(p.dropFirst(8))
        }
        return p
    }

    /// Safely updates quality scores for a specific photo by its unique identifier,
    /// avoiding index-based ABA race conditions during rapid navigation.
    public func updatePhotoScores(photoID: UUID, scores: QualityScores) {
        if let index = photos.firstIndex(where: { $0.id == photoID }) {
            photos[index].scores = scores
        }
    }

    /// Safely updates EXIF metadata for a specific photo by its unique identifier.
    public func updatePhotoExif(photoID: UUID, exif: ExifData) {
        if let index = photos.firstIndex(where: { $0.id == photoID }) {
            photos[index].exif = exif
        }
    }

    // MARK: - Internal Dependencies
    private let directoryScanner: DirectoryScanning
    private let thumbnailLoader: ThumbnailLoader
    private let thumbnailCache: ThumbnailCache
    private let cullingActor: AsyncCullingActor
    private let userDefaults: UserDefaults
    private var undoStack: [CullingExecutionRecord] = []
    private var toastDismissTask: Task<Void, Never>?
    private var scanTask: Task<Void, Never>?
    private var metadataTask: Task<Void, Never>?

    public var hasActivePhoto: Bool {
        photos.indices.contains(currentIndex)
    }

    public var currentPhoto: PhotoItem? {
        hasActivePhoto ? photos[currentIndex] : nil
    }

    /// Previous photo in sequence relative to currentIndex (index - 1), or nil at beginning of library.
    public var previousPhoto: PhotoItem? {
        guard currentIndex > 0, photos.indices.contains(currentIndex - 1) else { return nil }
        return photos[currentIndex - 1]
    }

    /// Next photo in sequence relative to currentIndex (index + 1), or nil at end of library.
    public var nextPhoto: PhotoItem? {
        guard currentIndex < photos.count - 1, photos.indices.contains(currentIndex + 1) else { return nil }
        return photos[currentIndex + 1]
    }

    public var comparisonSlotPhotos: [PhotoItem] {
        comparisonSlotIndices.compactMap { idx in
            photos.indices.contains(idx) ? photos[idx] : nil
        }
    }

    /// Cancels any active folder scanning operation and resets loading state.
    public func cancelLoading() {
        scanTask?.cancel()
        scanTask = nil
        metadataTask?.cancel()
        metadataTask = nil
        isLoading = false
    }

    public init(
        directoryScanner: DirectoryScanning = DirectoryScanner.shared,
        thumbnailLoader: ThumbnailLoader = ThumbnailLoader.shared,
        thumbnailCache: ThumbnailCache = ThumbnailCache.shared,
        cullingActor: AsyncCullingActor = AsyncCullingActor(),
        userDefaults: UserDefaults = .standard
    ) {
        self.directoryScanner = directoryScanner
        self.thumbnailLoader = thumbnailLoader
        self.thumbnailCache = thumbnailCache
        self.cullingActor = cullingActor
        self.userDefaults = userDefaults
        self.autoAdvance = userDefaults.autoAdvance
    }

    // MARK: - Folder Loading & Ingestion

    /// Opens an NSOpenPanel to let the user select a photo directory or mounted SD card.
    public func presentFolderPicker() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.allowsMultipleSelection = false
        panel.canCreateDirectories = false
        panel.prompt = "Open"
        panel.title = "Select Photo Folder or SD Card"

        if panel.runModal() == .OK, let selectedURL = panel.url {
            loadFolder(at: selectedURL)
        }
    }

    /// Handles drag-and-drop file URLs.
    public func handleDroppedURLs(_ urls: [URL]) -> Bool {
        guard let folderURL = urls.first(where: { (try? $0.resourceValues(forKeys: [.isDirectoryKey]))?.isDirectory == true }) else {
            return false
        }
        loadFolder(at: folderURL)
        return true
    }

    /// Size of the first streamed batch: one photograph, so the canvas renders as soon as the
    /// first directory listing returns. Subsequent batches ramp ×4 up to `scanBatchSize`.
    public static let firstScanBatchSize = 1
    public static let scanBatchSize = 30

    /// Loads photos from the specified directory via progressive streaming.
    public func loadFolder(at url: URL) {
        scanTask?.cancel()
        scanTask = nil
        metadataTask?.cancel()
        metadataTask = nil

        self.rootDirectoryURL = url
        self.photos = []
        self.currentIndex = 0
        self.activeSlotIndex = 0
        self.zoomState = .fit
        self.undoStack.removeAll()
        self.isLoading = true

        scanTask = Task { [weak self, directoryScanner] in
            var isFirstBatch = true
            for await batch in directoryScanner.scanStream(
                at: url,
                recursive: true,
                batchSize: Self.scanBatchSize,
                firstBatchSize: Self.firstScanBatchSize
            ) {
                if Task.isCancelled { break }
                guard let self else { break }
                let previousCount = self.photos.count
                self.photos.append(contentsOf: batch)
                if isFirstBatch, !self.photos.isEmpty {
                    isFirstBatch = false
                    self.currentIndex = 0
                    self.setupSlotsForMode(self.comparisonMode)
                } else if previousCount < self.comparisonMode.rawValue {
                    // Early batches could not fill every comparison slot yet.
                    self.setupSlotsForMode(self.comparisonMode)
                    self.prefetchAroundCurrentIndex()
                } else if previousCount <= self.currentIndex + 3 {
                    // The comparison neighbours / prefetch window just became available.
                    self.syncSlotsToCurrentIndex()
                    self.prefetchAroundCurrentIndex()
                }
            }
            guard let self, !Task.isCancelled else { return }
            self.isLoading = false
            self.startMetadataEnrichment()
        }
    }

    /// Resolves EXIF for every photo still missing it, off the main actor, publishing in
    /// batches (one array assignment per batch) so Library Statistics and the HUD fill in
    /// without per-photo re-render storms. Cancelled on folder change.
    private func startMetadataEnrichment() {
        metadataTask?.cancel()
        let targets: [(UUID, URL)] = photos.filter { $0.exif == nil }.map { ($0.id, $0.url) }
        guard !targets.isEmpty else { return }

        metadataTask = Task.detached(priority: .utility) { [weak self] in
            var pending: [UUID: ExifData] = [:]
            for (id, url) in targets {
                if Task.isCancelled { return }
                if let exif = Self.readMetadataWithCompanionFallback(for: url) {
                    pending[id] = exif
                }
                if pending.count >= 64 {
                    let flush = pending
                    pending.removeAll(keepingCapacity: true)
                    await self?.applyMetadata(flush)
                }
            }
            if !pending.isEmpty, !Task.isCancelled {
                await self?.applyMetadata(pending)
            }
        }
    }

    /// Reads EXIF from the primary file, falling back to companions (e.g. the JPEG of a
    /// RAW+JPEG pair) when the primary yields none. Runs off the main actor.
    public nonisolated static func readMetadataWithCompanionFallback(for url: URL) -> ExifData? {
        if let exif = ImageMetadataReader.readMetadata(from: url), !exif.isFallback {
            return exif
        }
        let companions = FileCullingManager().findCompanionFiles(for: url)
        for companion in companions where companion != url {
            if let exif = ImageMetadataReader.readMetadata(from: companion), !exif.isFallback {
                return exif
            }
        }
        return ImageMetadataReader.readMetadata(from: url)
    }

    private func applyMetadata(_ updates: [UUID: ExifData]) {
        var updated = photos
        var changed = false
        for index in updated.indices where updated[index].exif == nil {
            if let exif = updates[updated[index].id] {
                updated[index].exif = exif
                changed = true
            }
        }
        if changed {
            photos = updated
        }
    }

    /// Convenience alias for scanning a directory.
    public func scanDirectory(at url: URL) {
        loadFolder(at: url)
    }

    /// Sets photos directly for testing or programmatic injection.
    public func setPhotos(_ items: [PhotoItem], rootURL: URL? = nil) {
        self.rootDirectoryURL = rootURL
        self.photos = items
        self.currentIndex = 0
        self.activeSlotIndex = 0
        setupSlotsForMode(comparisonMode)
        prefetchAroundCurrentIndex()
    }

    // MARK: - Navigation

    public func navigateToNext() {
        guard !photos.isEmpty else { return }
        switch comparisonMode {
        case .single, .triplet:
            if currentIndex < photos.count - 1 {
                currentIndex += 1
            }
        case .sideBySide:
            advanceActiveSlotToNextCandidate()
        }
    }

    public func navigateToPrevious() {
        guard !photos.isEmpty else { return }
        switch comparisonMode {
        case .single, .triplet:
            if currentIndex > 0 {
                currentIndex -= 1
            }
        case .sideBySide:
            stepActiveSlotToPreviousCandidate()
        }
    }

    public func selectPhoto(at index: Int) {
        guard photos.indices.contains(index) else { return }
        switch comparisonMode {
        case .single, .triplet:
            currentIndex = index
        case .sideBySide:
            if comparisonSlotIndices.indices.contains(activeSlotIndex) {
                comparisonSlotIndices[activeSlotIndex] = index
            }
            currentIndex = index
        }
    }

    public func setComparisonMode(_ mode: ComparisonMode) {
        guard comparisonMode != mode else { return }
        comparisonMode = mode
        setupSlotsForMode(mode)
        showToast(
            icon: mode.systemImage,
            title: "\(mode.title) View",
            subtitle: nil
        )
    }

    public func advanceComparisonSlotFocus() {
        switch comparisonMode {
        case .single:
            navigateToNext()
        case .sideBySide, .triplet:
            activeSlotIndex = (activeSlotIndex + 1) % max(1, comparisonSlotIndices.count)
        }
    }

    public func setActiveSlot(_ index: Int) {
        guard index >= 0, index < comparisonMode.rawValue else { return }
        activeSlotIndex = index
        switch comparisonMode {
        case .single:
            break
        case .sideBySide:
            if comparisonSlotIndices.indices.contains(index) {
                currentIndex = comparisonSlotIndices[index]
            }
        case .triplet:
            if comparisonSlotIndices.indices.contains(index) {
                currentIndex = comparisonSlotIndices[index]
            }
        }
    }

    /// Space / "100%" button / double-click: Fit → 100 %; any zoomed state → Fit.
    public func toggleZoom() {
        if zoomState.isZoomed {
            resetZoomToFit()
        } else {
            zoomState = .oneToOne
            showToast(
                icon: "viewfinder.circle.fill",
                title: "100% Zoom (1:1)",
                subtitle: "Esc or Space returns to Fit"
            )
        }
    }

    /// Escape / "Fit" pill: leaves any zoom (100 % or pinch) and returns to Fit.
    /// Returns `true` when a zoom was actually exited.
    @discardableResult
    public func resetZoomToFit() -> Bool {
        guard zoomState.isZoomed else { return false }
        zoomState = .fit
        showToast(
            icon: "arrow.up.left.and.down.right.and.arrow.up.right.and.down.left",
            title: "Fit to Window",
            subtitle: nil
        )
        return true
    }

    /// Trackpad pinch ended at `scale` (relative to Fit). Small residual scales snap to Fit.
    public func setPinchMagnification(_ scale: Double) {
        if scale <= ZoomState.snapToFitThreshold {
            zoomState = .fit
        } else {
            zoomState = .magnified(min(scale, 8.0))
        }
    }

    public func toggleZoomSync() {
        isZoomSynced.toggle()
        showToast(
            icon: isZoomSynced ? "link" : "link.badge.slash",
            title: isZoomSynced ? "Pan Linked" : "Pan Independent",
            subtitle: nil
        )
    }

    // MARK: - Zero-Latency Culling (< 1.0ms Optimistic State Mutation)

    public func cullCurrentPhoto(action: CullingActionType) {
        guard !photos.isEmpty else { return }
        let targetIndex: Int
        if comparisonMode == .sideBySide, comparisonSlotIndices.indices.contains(activeSlotIndex) {
            targetIndex = comparisonSlotIndices[activeSlotIndex]
        } else {
            guard photos.indices.contains(currentIndex) else { return }
            targetIndex = currentIndex
        }
        guard photos.indices.contains(targetIndex) else { return }
        let photo = photos[targetIndex]
        guard let rootURL = rootDirectoryURL ?? photo.url.deletingLastPathComponent() as URL? else {
            return
        }

        let previousStatus = photo.status

        // 1. Instant Optimistic State Mutation (< 1.0ms)
        let newStatus: PhotoStatus
        let toastTitle: String
        let toastIcon: String

        let destinationFolder = userDefaults.cullingDestinationFolderName

        switch action {
        case .move:
            newStatus = .selected
            toastTitle = "Moved to \(destinationFolder)"
            toastIcon = "checkmark.circle.fill"
        case .copy:
            newStatus = .copied
            toastTitle = "Copied to \(destinationFolder)"
            toastIcon = "doc.on.doc.fill"
        case .trash:
            newStatus = .trashed
            toastTitle = "Moved to Trash"
            toastIcon = "trash.fill"
        }

        photos[targetIndex].status = newStatus

        // 2. Immediate Toast HUD
        showToast(
            icon: toastIcon,
            title: toastTitle,
            subtitle: photo.filename,
            actionType: action
        )

        // 3. Auto-Advance (Slides candidate sequence forward)
        if autoAdvance {
            if comparisonMode == .sideBySide {
                advanceActiveSlotToNextCandidate()
            } else {
                navigateToNext()
            }
        }

        // 4. Background Serial Execution on AsyncCullingActor
        let job = CullingJob(
            photoID: photo.id,
            actionType: action,
            primaryURL: photo.url,
            rootURL: rootURL,
            destinationFolderName: destinationFolder
        )

        // Optimistic undo record: affectedURLs is empty because no destination files exist yet
        let optimisticRecord = CullingExecutionRecord(
            jobID: job.id,
            photoID: photo.id,
            actionType: action,
            originalPrimaryURL: photo.url,
            affectedURLs: [],
            previousStatus: previousStatus
        )
        undoStack.append(optimisticRecord)

        Task { [weak self, cullingActor] in
            do {
                guard let record = try await cullingActor.enqueue(job) else { return }
                await MainActor.run {
                    if let idx = self?.undoStack.firstIndex(where: { $0.jobID == record.jobID }) {
                        let preservedPreviousStatus = self?.undoStack[idx].previousStatus ?? previousStatus
                        self?.undoStack[idx] = CullingExecutionRecord(
                            jobID: record.jobID,
                            photoID: record.photoID,
                            actionType: record.actionType,
                            originalPrimaryURL: record.originalPrimaryURL,
                            affectedURLs: record.affectedURLs,
                            previousStatus: preservedPreviousStatus,
                            timestamp: record.timestamp
                        )
                    }
                }
            } catch is CancellationError {
                // Cancelled before execution
            } catch {
                await MainActor.run {
                    self?.undoStack.removeAll(where: { $0.jobID == job.id })
                    self?.rollback(photoIndex: targetIndex, previousStatus: previousStatus, error: error)
                }
            }
        }
    }

    /// Convenience alias: Move current photo to Selection.
    public func selectCurrentPhoto() {
        cullCurrentPhoto(action: .move)
    }

    /// Convenience alias: Copy current photo to Selection.
    public func copyCurrentPhoto() {
        cullCurrentPhoto(action: .copy)
    }

    /// Convenience alias: Move current photo to Trash.
    public func trashCurrentPhoto() {
        cullCurrentPhoto(action: .trash)
    }

    // MARK: - Undo Support

    public func undoLastAction() {
        guard let record = undoStack.popLast() else { return }

        // Find photo in list
        if let index = photos.firstIndex(where: { $0.id == record.photoID }) {
            // 1. Immediate UI Restoration (< 1ms)
            photos[index].status = record.previousStatus
            selectPhoto(at: index)
        }

        showToast(
            icon: "arrow.uturn.backward.circle.fill",
            title: "Action Undone",
            subtitle: record.originalPrimaryURL.lastPathComponent
        )

        // 2. Background Reversal and in-flight cancellation on Actor
        Task { [weak self, cullingActor] in
            do {
                _ = try await cullingActor.cancelOrUndo(jobID: record.jobID, fallbackRecord: record)
            } catch {
                await MainActor.run {
                    self?.showToast(
                        icon: "exclamationmark.triangle.fill",
                        title: "Undo Failed",
                        subtitle: error.localizedDescription
                    )
                    NSSound.beep()
                }
            }
        }
    }

    /// Convenience alias: Undo last action.
    public func undo() {
        undoLastAction()
    }

    // MARK: - Private Helpers

    private func advanceActiveSlotToNextCandidate() {
        guard comparisonMode == .sideBySide else {
            navigateToNext()
            return
        }
        guard comparisonSlotIndices.indices.contains(activeSlotIndex) else { return }
        let currentSlotValue = comparisonSlotIndices[activeSlotIndex]
        let otherSlots = Set(comparisonSlotIndices.enumerated().filter { $0.offset != activeSlotIndex }.map { $0.element })

        // Find next candidate with index > currentSlotValue
        if let nextCandidate = (currentSlotValue + 1..<photos.count).first(where: { idx in
            photos[idx].status == .candidate && !otherSlots.contains(idx)
        }) {
            comparisonSlotIndices[activeSlotIndex] = nextCandidate
            currentIndex = nextCandidate
        } else if let anyCandidate = photos.indices.first(where: { idx in
            photos[idx].status == .candidate && !otherSlots.contains(idx) && idx != currentSlotValue
        }) {
            comparisonSlotIndices[activeSlotIndex] = anyCandidate
            currentIndex = anyCandidate
        }
    }

    private func stepActiveSlotToPreviousCandidate() {
        guard comparisonMode == .sideBySide else {
            navigateToPrevious()
            return
        }
        guard comparisonSlotIndices.indices.contains(activeSlotIndex) else { return }
        let currentSlotValue = comparisonSlotIndices[activeSlotIndex]
        let otherSlots = Set(comparisonSlotIndices.enumerated().filter { $0.offset != activeSlotIndex }.map { $0.element })

        // Find previous candidate with index < currentSlotValue
        if let prevCandidate = (0..<currentSlotValue).reversed().first(where: { idx in
            photos[idx].status == .candidate && !otherSlots.contains(idx)
        }) {
            comparisonSlotIndices[activeSlotIndex] = prevCandidate
            currentIndex = prevCandidate
        }
    }

    private func setupSlotsForMode(_ mode: ComparisonMode) {
        guard !photos.isEmpty else {
            comparisonSlotIndices = []
            activeSlotIndex = 0
            return
        }
        switch mode {
        case .single:
            comparisonSlotIndices = [currentIndex]
            activeSlotIndex = 0
        case .sideBySide:
            let next = min(photos.count - 1, currentIndex + 1)
            comparisonSlotIndices = [currentIndex, next]
            activeSlotIndex = 0
        case .triplet:
            if photos.count >= 3 {
                if currentIndex == 0 {
                    comparisonSlotIndices = [0, 1, 2]
                    activeSlotIndex = 0
                } else if currentIndex == photos.count - 1 {
                    comparisonSlotIndices = [photos.count - 3, photos.count - 2, photos.count - 1]
                    activeSlotIndex = 2
                } else {
                    comparisonSlotIndices = [currentIndex - 1, currentIndex, currentIndex + 1]
                    activeSlotIndex = 1
                }
            } else {
                let prev = max(0, currentIndex - 1)
                let next = min(photos.count - 1, currentIndex + 1)
                comparisonSlotIndices = [prev, currentIndex, next]
                activeSlotIndex = (currentIndex == 0 ? 0 : 1)
            }
        }
    }

    private func syncSlotsToCurrentIndex() {
        guard !photos.isEmpty else {
            comparisonSlotIndices = []
            return
        }
        switch comparisonMode {
        case .single:
            comparisonSlotIndices = [currentIndex]
            activeSlotIndex = 0
        case .sideBySide:
            if comparisonSlotIndices.count != 2 {
                let next = min(photos.count - 1, currentIndex + 1)
                comparisonSlotIndices = [currentIndex, next]
                activeSlotIndex = 0
            } else if comparisonSlotIndices.indices.contains(activeSlotIndex) {
                comparisonSlotIndices[activeSlotIndex] = currentIndex
            }
        case .triplet:
            if photos.count >= 3 {
                if currentIndex == 0 {
                    comparisonSlotIndices = [0, 1, 2]
                } else if currentIndex == photos.count - 1 {
                    comparisonSlotIndices = [photos.count - 3, photos.count - 2, photos.count - 1]
                } else {
                    comparisonSlotIndices = [currentIndex - 1, currentIndex, currentIndex + 1]
                }
            } else {
                let prev = max(0, currentIndex - 1)
                let next = min(photos.count - 1, currentIndex + 1)
                comparisonSlotIndices = [prev, currentIndex, next]
            }
            if let idx = comparisonSlotIndices.firstIndex(of: currentIndex) {
                activeSlotIndex = idx
            }
        }
    }

    private func prefetchAroundCurrentIndex() {
        guard !photos.isEmpty else { return }
        let windowBefore = max(1, min(userDefaults.preloadWindowBefore, 5))
        let windowAfter = max(2, min(userDefaults.preloadWindowAfter, 10))
        Task { [weak self, thumbnailLoader] in
            guard let self else { return }
            await thumbnailLoader.updatePrefetchWindow(
                items: self.photos,
                currentIndex: self.currentIndex,
                windowBefore: windowBefore,
                windowAfter: windowAfter,
                maxPixelSize: ThumbnailTier.preview.rawValue
            )
        }
    }

    private func rollback(photoIndex: Int, previousStatus: PhotoStatus, error: Error) {
        guard photos.indices.contains(photoIndex) else { return }
        photos[photoIndex].status = previousStatus
        showToast(
            icon: "exclamationmark.triangle.fill",
            title: "Action Failed",
            subtitle: error.localizedDescription
        )
        NSSound.beep()
    }

    private func showToast(
        icon: String,
        title: String,
        subtitle: String?,
        actionType: CullingActionType? = nil
    ) {
        toastDismissTask?.cancel()
        currentToast = ToastMessage(icon: icon, title: title, subtitle: subtitle, actionType: actionType)
        toastDismissTask = Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000) // 1.2 seconds
            if !Task.isCancelled {
                currentToast = nil
            }
        }
    }
}
