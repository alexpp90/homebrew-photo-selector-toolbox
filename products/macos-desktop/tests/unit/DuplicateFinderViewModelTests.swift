import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("Duplicate Finder ViewModel & Logic Unit Tests")
struct DuplicateFinderViewModelTests {

    private func createTempFile(name: String, content: Data, in directory: URL) throws -> URL {
        let fileURL = directory.appendingPathComponent(name)
        try content.write(to: fileURL)
        return fileURL
    }

    @Test("Duplicate scanning and clustering: accurately groups identical content")
    @MainActor
    func testDuplicateScanningAndClustering() async throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("DupScanTest_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let duplicateBytes = Data(repeating: 0x42, count: 2048)
        let uniqueBytes = Data(repeating: 0x99, count: 2048)

        let dup1 = try createTempFile(name: "photo_A.jpg", content: duplicateBytes, in: tempDir)
        let dup2 = try createTempFile(name: "photo_B.jpg", content: duplicateBytes, in: tempDir)
        _ = try createTempFile(name: "unique.jpg", content: uniqueBytes, in: tempDir)

        let viewModel = DuplicateFinderViewModel(targetDirectoryURL: tempDir)
        viewModel.startScan()

        // Wait for asynchronous scan to complete
        var attempts = 0
        while viewModel.isScanning && attempts < 50 {
            try await Task.sleep(nanoseconds: 50_000_000)
            attempts += 1
        }

        #expect(viewModel.scanState == .completed)
        #expect(viewModel.duplicateGroups.count == 1)

        let group = viewModel.duplicateGroups[0]
        #expect(group.entries.count == 2)
        #expect(group.wastedBytes == 2048)
        #expect(group.fileSize == 2048)
        let foundURLs = Set(group.entries.map { $0.url })
        #expect(foundURLs.contains(dup1))
        #expect(foundURLs.contains(dup2))
    }

    @Test("Keep Newest: preserves the newest file and marks older duplicates for trash")
    @MainActor
    func testKeepNewestMarksOlderFiles() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("DupNewestTest_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let bytes = Data(repeating: 0x55, count: 1024)
        let fileOld = try createTempFile(name: "old.jpg", content: bytes, in: tempDir)
        let fileMid = try createTempFile(name: "mid.jpg", content: bytes, in: tempDir)
        let fileNew = try createTempFile(name: "new.jpg", content: bytes, in: tempDir)

        let now = Date()
        let entryOld = DuplicateFileEntry(url: fileOld, fileSize: 1024, captureDate: now.addingTimeInterval(-3600))
        let entryMid = DuplicateFileEntry(url: fileMid, fileSize: 1024, captureDate: now.addingTimeInterval(-1800))
        let entryNew = DuplicateFileEntry(url: fileNew, fileSize: 1024, captureDate: now)

        let group = DuplicateGroupModel(
            hash: "dummyhash_newest",
            fileSize: 1024,
            formattedFileSize: "1 KB",
            entries: [entryOld, entryMid, entryNew]
        )

        let viewModel = DuplicateFinderViewModel(targetDirectoryURL: tempDir)
        viewModel.setDuplicateGroupsForTesting([group])

        #expect(viewModel.duplicateGroups.count == 1)
        #expect(viewModel.duplicateGroups[0].entries.allSatisfy { !$0.isMarkedForTrash })

        // Execute Keep Newest on the ViewModel
        viewModel.keepNewest(in: group.id)

        let updatedEntries = viewModel.duplicateGroups[0].entries
        #expect(updatedEntries[0].isMarkedForTrash == true)
        #expect(updatedEntries[1].isMarkedForTrash == true)
        #expect(updatedEntries[2].isMarkedForTrash == false, "Newest entry must not be marked for trash")
        #expect(viewModel.markedForTrashCount == 2)
    }

    @Test("Keep Highest Score: preserves highest scoring photo and marks lower scores")
    @MainActor
    func testKeepHighestScore() async throws {
        let u1 = URL(fileURLWithPath: "/tmp/photo1.jpg")
        let u2 = URL(fileURLWithPath: "/tmp/photo2.jpg")
        let u3 = URL(fileURLWithPath: "/tmp/photo3.jpg")

        let scores1 = QualityScores(sharpness: 30.0, noise: 0, highlightClipping: 0, shadowClipping: 0, aesthetic: 3.0)
        let scores2 = QualityScores(sharpness: 90.0, noise: 0, highlightClipping: 0, shadowClipping: 0, aesthetic: 8.5) // Best
        let scores3 = QualityScores(sharpness: 60.0, noise: 0, highlightClipping: 0, shadowClipping: 0, aesthetic: 6.0)

        let entry1 = DuplicateFileEntry(url: u1, fileSize: 100, captureDate: nil, scores: scores1)
        let entry2 = DuplicateFileEntry(url: u2, fileSize: 100, captureDate: nil, scores: scores2)
        let entry3 = DuplicateFileEntry(url: u3, fileSize: 100, captureDate: nil, scores: scores3)

        let group = DuplicateGroupModel(
            hash: "dummyhash_score",
            fileSize: 100,
            formattedFileSize: "100 B",
            entries: [entry1, entry2, entry3]
        )

        let viewModel = DuplicateFinderViewModel()
        viewModel.setDuplicateGroupsForTesting([group])

        // Execute Keep Highest Score on the ViewModel
        await viewModel.keepHighestScore(in: group.id)

        let updatedEntries = viewModel.duplicateGroups[0].entries
        #expect(updatedEntries[0].isMarkedForTrash == true)
        #expect(updatedEntries[1].isMarkedForTrash == false, "Highest scoring entry must be preserved")
        #expect(updatedEntries[2].isMarkedForTrash == true)
        #expect(viewModel.markedForTrashCount == 2)
    }

    @Test("Companion-safe trashing: deletes duplicate and companion files, preserving kept file")
    @MainActor
    func testCompanionSafeTrashing() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("DupCompanionTrash_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let keptURL = try createTempFile(name: "IMG_0001.arw", content: Data(repeating: 0x11, count: 512), in: tempDir)
        let dupURL = try createTempFile(name: "IMG_0002.arw", content: Data(repeating: 0x11, count: 512), in: tempDir)
        let companionXMP = try createTempFile(name: "IMG_0002.xmp", content: Data("<xmp/>".utf8), in: tempDir)

        var trashedCallbackURLs: [URL] = []
        let viewModel = DuplicateFinderViewModel(
            targetDirectoryURL: tempDir,
            onPhotoTrashed: { url in
                trashedCallbackURLs.append(url)
            }
        )

        let entryKept = DuplicateFileEntry(url: keptURL, fileSize: 512, captureDate: nil, isMarkedForTrash: false)
        let entryDup = DuplicateFileEntry(url: dupURL, fileSize: 512, captureDate: nil, isMarkedForTrash: true)

        let group = DuplicateGroupModel(hash: "hash_comp", fileSize: 512, formattedFileSize: "512 B", entries: [entryKept, entryDup])
        viewModel.setDuplicateGroupsForTesting([group])

        // Trashing single entry via ViewModel
        viewModel.trashSingleEntry(entry: entryDup, in: group.id)

        #expect(trashedCallbackURLs.contains(dupURL))
        #expect(FileManager.default.fileExists(atPath: keptURL.path))
        #expect(!FileManager.default.fileExists(atPath: dupURL.path))
        #expect(!FileManager.default.fileExists(atPath: companionXMP.path), "Companion XMP sidecar must be trashed along with primary")
        #expect(viewModel.duplicateGroups[0].entries.first(where: { $0.url == dupURL })?.status == .trashed)
    }

    @Test("Batch Trashing: trashMarkedDuplicates deletes marked files and clears resolved groups")
    @MainActor
    func testBatchTrashMarkedDuplicates() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("DupBatchTrash_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let keptURL = try createTempFile(name: "keep.jpg", content: Data(repeating: 0x22, count: 256), in: tempDir)
        let trash1 = try createTempFile(name: "trash1.jpg", content: Data(repeating: 0x22, count: 256), in: tempDir)
        let trash2 = try createTempFile(name: "trash2.jpg", content: Data(repeating: 0x22, count: 256), in: tempDir)

        let entryKept = DuplicateFileEntry(url: keptURL, fileSize: 256, captureDate: nil, isMarkedForTrash: false)
        let entry1 = DuplicateFileEntry(url: trash1, fileSize: 256, captureDate: nil, isMarkedForTrash: true)
        let entry2 = DuplicateFileEntry(url: trash2, fileSize: 256, captureDate: nil, isMarkedForTrash: true)

        let group = DuplicateGroupModel(hash: "batch_hash", fileSize: 256, formattedFileSize: "256 B", entries: [entryKept, entry1, entry2])

        let viewModel = DuplicateFinderViewModel(targetDirectoryURL: tempDir)
        viewModel.setDuplicateGroupsForTesting([group])

        #expect(viewModel.markedForTrashCount == 2)
        viewModel.trashMarkedDuplicates()

        #expect(FileManager.default.fileExists(atPath: keptURL.path))
        #expect(!FileManager.default.fileExists(atPath: trash1.path))
        #expect(!FileManager.default.fileExists(atPath: trash2.path))
        // Fully resolved group (only 1 non-trashed entry remaining) should be purged from duplicateGroups
        #expect(viewModel.duplicateGroups.isEmpty)
    }

    @Test("Manual selection actions: toggleTrashMark and markToKeep alter entry state")
    @MainActor
    func testToggleTrashMarkAndMarkToKeep() {
        let u1 = URL(fileURLWithPath: "/tmp/a.jpg")
        let u2 = URL(fileURLWithPath: "/tmp/b.jpg")

        let e1 = DuplicateFileEntry(url: u1, fileSize: 100, captureDate: nil, isMarkedForTrash: false)
        let e2 = DuplicateFileEntry(url: u2, fileSize: 100, captureDate: nil, isMarkedForTrash: false)
        let group = DuplicateGroupModel(hash: "h_manual", fileSize: 100, formattedFileSize: "100 B", entries: [e1, e2])

        let viewModel = DuplicateFinderViewModel()
        viewModel.setDuplicateGroupsForTesting([group])

        // Toggle u1
        viewModel.toggleTrashMark(entryID: u1, in: group.id)
        #expect(viewModel.duplicateGroups[0].entries[0].isMarkedForTrash == true)

        // Toggle u1 back
        viewModel.toggleTrashMark(entryID: u1, in: group.id)
        #expect(viewModel.duplicateGroups[0].entries[0].isMarkedForTrash == false)

        // markToKeep u1 -> u2 marked for trash, u1 kept
        viewModel.markToKeep(entryID: u1, in: group.id)
        #expect(viewModel.duplicateGroups[0].entries[0].isMarkedForTrash == false)
        #expect(viewModel.duplicateGroups[0].entries[1].isMarkedForTrash == true)
    }

    @Test("Empty directory scan: returns empty state when no duplicates exist")
    @MainActor
    func testEmptyDirectoryScan() async throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("DupEmptyTest_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        _ = try createTempFile(name: "unique1.jpg", content: Data("Unique1".utf8), in: tempDir)
        _ = try createTempFile(name: "unique2.jpg", content: Data("Unique2".utf8), in: tempDir)

        let viewModel = DuplicateFinderViewModel(targetDirectoryURL: tempDir)
        viewModel.startScan()

        var attempts = 0
        while viewModel.isScanning && attempts < 50 {
            try await Task.sleep(nanoseconds: 50_000_000)
            attempts += 1
        }

        #expect(viewModel.scanState == .empty)
        #expect(viewModel.duplicateGroups.isEmpty)
    }
}
