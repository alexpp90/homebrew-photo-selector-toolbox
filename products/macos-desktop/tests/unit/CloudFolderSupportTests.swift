import Testing
import Foundation
@testable import PhotoSelectorKit

/// Deterministic fake `CloudResourceInspecting` for testing File Provider / cloud folder behavior.
struct FakeCloudFolderInspector: CloudResourceInspecting {
    var remoteFolders: Set<URL> = []
    var onlineOnlyURLs: Set<URL> = []
    var sharedURLs: Set<URL> = []
    var fileSizes: [URL: Int64] = [:]
    var treatAllAsRemote: Bool = false

    func isRemoteOrCloudFolder(_ url: URL) -> Bool {
        if treatAllAsRemote { return true }
        let std = url.standardizedFileURL.resolvingSymlinksInPath()
        return remoteFolders.contains(url) || remoteFolders.contains(std)
    }

    func isOnlineOnlyItem(_ url: URL) -> Bool {
        let std = url.standardizedFileURL.resolvingSymlinksInPath()
        return onlineOnlyURLs.contains(url) || onlineOnlyURLs.contains(std)
    }

    func isSharedItem(_ url: URL) -> Bool {
        let std = url.standardizedFileURL.resolvingSymlinksInPath()
        return sharedURLs.contains(url) || sharedURLs.contains(std)
    }

    func estimatedDownloadBytes(for urls: [URL]) -> Int64 {
        urls.reduce(0) { sum, url in
            let std = url.standardizedFileURL.resolvingSymlinksInPath()
            return sum + (fileSizes[url] ?? fileSizes[std] ?? 0)
        }
    }
}

/// Fake `TrashService` that simulates a cloud File Provider refusing system Trash.
struct RefusingCloudTrashService: TrashService {
    func trash(_ url: URL) throws -> URL {
        throw FileCullingError.trashFailed(
            url: url,
            reason: "File Provider does not support moving items to Trash."
        )
    }
}

@Suite("U-29: Cloud & File Provider Folder Support Tests")
struct CloudFolderSupportTests {

    private func createTempDir(prefix: String) throws -> URL {
        let tempBase = FileManager.default.temporaryDirectory
            .standardizedFileURL
            .resolvingSymlinksInPath()
        let dir = tempBase.appendingPathComponent("\(prefix)_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    @Test("Remote folder detection sets isRemoteFolder, publishes Cloud Folder badge, and clamps prefetchRadius to 1")
    @MainActor
    func testRemoteFolderDetectionBadgeAndOneAheadPrefetch() async throws {
        let dir = try createTempDir(prefix: "CloudFolderBadge")
        defer { try? FileManager.default.removeItem(at: dir) }

        let p1 = dir.appendingPathComponent("IMG_0001.JPG")
        let p2 = dir.appendingPathComponent("IMG_0002.JPG")
        let p3 = dir.appendingPathComponent("IMG_0003.JPG")
        let p4 = dir.appendingPathComponent("IMG_0004.JPG")
        for u in [p1, p2, p3, p4] {
            try Data("JPEG".utf8).write(to: u)
        }

        let inspector = FakeCloudFolderInspector(remoteFolders: [dir], treatAllAsRemote: true)
        let cache = ThumbnailCache()
        let loader = ThumbnailLoader(cache: cache)
        let vm = CullingWorkspaceViewModel(
            thumbnailLoader: loader,
            thumbnailCache: cache,
            cloudInspector: inspector
        )

        let items = [p1, p2, p3, p4].map { PhotoItem(url: $0) }
        vm.setPhotos(items, rootURL: dir)

        #expect(vm.isRemoteFolder == true)
        #expect(vm.cloudBadgeText == "Cloud Folder")
        #expect(vm.prefetchRadius == 1)

        // Allow the async prefetch task to register its keys
        try await Task.sleep(nanoseconds: 50_000_000)
        await loader.cancelAll()
    }

    @Test("Cloud folder suppresses automatic full-folder pass until confirmAndRunFullPass and estimates download size")
    @MainActor
    func testNoAutomaticFullFolderPassAndEstimatedDownloadBytes() async throws {
        let dir = try createTempDir(prefix: "CloudFullPass")
        defer { try? FileManager.default.removeItem(at: dir) }

        let u1 = dir.appendingPathComponent("DSC0001.ARW")
        let u2 = dir.appendingPathComponent("DSC0002.ARW")
        let u3 = dir.appendingPathComponent("DSC0003.ARW")
        try Data("1".utf8).write(to: u1)
        try Data("2".utf8).write(to: u2)
        try Data("3".utf8).write(to: u3)

        // u1 and u2 are online-only placeholders (25 MB and 30 MB); u3 is already downloaded locally (40 MB)
        let inspector = FakeCloudFolderInspector(
            remoteFolders: [dir],
            onlineOnlyURLs: [u1, u2],
            fileSizes: [
                u1: 25_000_000,
                u2: 30_000_000,
                u3: 40_000_000
            ],
            treatAllAsRemote: true
        )

        let vm = CullingWorkspaceViewModel(cloudInspector: inspector)
        vm.loadFolder(at: dir)

        // Wait for progressive scan to finish
        for _ in 0..<40 {
            if !vm.isLoading && vm.photos.count == 3 { break }
            try await Task.sleep(nanoseconds: 25_000_000)
        }

        #expect(vm.photos.count == 3)
        #expect(vm.isRemoteFolder == true)
        #expect(vm.requiresFullPassConfirmation == true)
        #expect(vm.estimatedFullPassDownloadBytes == 55_000_000)

        // Confirming the full pass clears requiresFullPassConfirmation
        vm.confirmAndRunFullPass()
        #expect(vm.requiresFullPassConfirmation == false)
    }

    @Test("Trash refusal on shared cloud folder publishes permanent-delete confirmation without Undo and includes shared warning")
    @MainActor
    func testTrashRefusalOnSharedCloudFolderTriggersConfirmationWithoutUndo() async throws {
        let dir = try createTempDir(prefix: "SharedCloudTrash")
        defer { try? FileManager.default.removeItem(at: dir) }

        let rawURL = dir.appendingPathComponent("IMG_0100.CR3")
        let xmpURL = dir.appendingPathComponent("IMG_0100.xmp")
        try Data("RAW_BYTES".utf8).write(to: rawURL)
        try Data("XMP_BYTES".utf8).write(to: xmpURL)

        let inspector = FakeCloudFolderInspector(
            remoteFolders: [dir],
            sharedURLs: [rawURL, dir],
            treatAllAsRemote: true
        )
        let actor = AsyncCullingActor(trashService: RefusingCloudTrashService())
        let vm = CullingWorkspaceViewModel(
            cullingActor: actor,
            cloudInspector: inspector
        )
        vm.autoAdvance = false
        vm.setPhotos([PhotoItem(url: rawURL)], rootURL: dir)

        vm.cullCurrentPhoto(action: .trash)

        // Wait for AsyncCullingActor to reject trash and publish pendingPermanentDelete
        for _ in 0..<40 {
            if vm.pendingPermanentDelete != nil { break }
            try await Task.sleep(nanoseconds: 25_000_000)
        }

        let confirmation = try #require(vm.pendingPermanentDelete)
        #expect(confirmation.isShared == true)
        #expect(confirmation.message.contains("removes it for everyone sharing this folder"))
        #expect(vm.canUndo == false, "Refused cloud trash must not leave an entry on the undo stack")
        #expect(vm.photos[0].status == .candidate, "Photo must remain candidate until permanent delete is confirmed")
        #expect(FileManager.default.fileExists(atPath: rawURL.path))
        #expect(FileManager.default.fileExists(atPath: xmpURL.path))

        // Confirming permanent delete removes primary + companion files and does not offer Undo
        vm.confirmPermanentDelete()
        #expect(vm.pendingPermanentDelete == nil)
        #expect(vm.photos[0].status == .trashed)
        #expect(vm.canUndo == false)
        #expect(!FileManager.default.fileExists(atPath: rawURL.path))
        #expect(!FileManager.default.fileExists(atPath: xmpURL.path))
    }
}
