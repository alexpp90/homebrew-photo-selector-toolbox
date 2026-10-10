import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("Focus View & Culling Affordance Unit Tests")
struct FocusViewAndCullingAffordanceTests {

    @Test("REQ-MAC-THEME.01, REQ-MAC-LAYOUT.01: Culling Workspace View Model: Culling counters and undo availability affordances")
    @MainActor
    func testCullingCountersAndAffordances() async throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let photos = (1...5).map { idx -> PhotoItem in
            let url = tempDir.appendingPathComponent("IMG_\(idx).JPG")
            FileManager.default.createFile(atPath: url.path, contents: Data([0x01, 0x02]), attributes: nil)
            return PhotoItem(url: url)
        }

        let viewModel = CullingWorkspaceViewModel()
        viewModel.setPhotos(photos, rootURL: tempDir)

        #expect(viewModel.selectedCount == 0)
        #expect(viewModel.trashedCount == 0)
        #expect(!viewModel.canUndo)

        // 1. Move Photo (Select)
        viewModel.selectCurrentPhoto()
        #expect(viewModel.selectedCount == 1)
        #expect(viewModel.canUndo)

        // 2. Trash Photo
        viewModel.trashCurrentPhoto()
        #expect(viewModel.trashedCount == 1)

        // 3. Undo Trash
        viewModel.undo()
        #expect(viewModel.trashedCount == 0)

        // 4. Undo Move
        viewModel.undo()
        #expect(viewModel.selectedCount == 0)
        #expect(!viewModel.canUndo)
    }

    @Test("Focus 3-Up Mode: Sliding triplet candidate sequence, navigation, and culling")
    @MainActor
    func testFocus3UpSlotActivationAndTargetedCulling() async throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let photos = (1...6).map { idx -> PhotoItem in
            let url = tempDir.appendingPathComponent("IMG_\(idx).JPG")
            FileManager.default.createFile(atPath: url.path, contents: Data([0x01, 0x02]), attributes: nil)
            return PhotoItem(url: url)
        }

        let viewModel = CullingWorkspaceViewModel()
        viewModel.setPhotos(photos, rootURL: tempDir)

        // Switch to Triplet Focus Mode (3-Up)
        viewModel.setComparisonMode(.triplet)
        #expect(viewModel.comparisonMode == .triplet)
        #expect(viewModel.comparisonSlotIndices.count == 3)
        #expect(viewModel.comparisonSlotIndices == [0, 1, 2])
        #expect(viewModel.activeSlotIndex == 0)

        // Verify initial candidate sequence
        #expect(viewModel.comparisonSlotPhotos[0].filename == "IMG_1.JPG")
        #expect(viewModel.comparisonSlotPhotos[1].filename == "IMG_2.JPG")
        #expect(viewModel.comparisonSlotPhotos[2].filename == "IMG_3.JPG")

        // Navigate to Candidate 2 (index 1 in sequence)
        viewModel.navigateToNext()
        #expect(viewModel.currentIndex == 1)
        #expect(viewModel.activeSlotIndex == 1)
        #expect(viewModel.previousPhoto?.filename == "IMG_1.JPG")
        #expect(viewModel.currentPhoto?.filename == "IMG_2.JPG")
        #expect(viewModel.nextPhoto?.filename == "IMG_3.JPG")

        // Cull current photo (index 1) with Copy
        viewModel.copyCurrentPhoto()
        #expect(viewModel.photos[1].status == .copied)
        // With autoAdvance, sequence slides forward to index 2
        #expect(viewModel.currentIndex == 2)
        #expect(viewModel.comparisonSlotIndices == [1, 2, 3])
        #expect(viewModel.previousPhoto?.filename == "IMG_2.JPG")
        #expect(viewModel.currentPhoto?.filename == "IMG_3.JPG")
        #expect(viewModel.nextPhoto?.filename == "IMG_4.JPG")

        // Select first photo via filmstrip selection
        viewModel.selectPhoto(at: 0)
        #expect(viewModel.currentIndex == 0)
        #expect(viewModel.previousPhoto == nil)
        #expect(viewModel.currentPhoto?.filename == "IMG_1.JPG")
        #expect(viewModel.nextPhoto?.filename == "IMG_2.JPG")

        // Move current photo to Selection
        viewModel.selectCurrentPhoto()
        #expect(viewModel.photos[0].status == .selected)
        // Auto-advance moves to index 1
        #expect(viewModel.currentIndex == 1)
        #expect(viewModel.currentPhoto?.filename == "IMG_2.JPG")
    }

    @Test("Shortcuts Guide Sheet: ViewModel presentation toggle state")
    @MainActor
    func testShortcutsHelpPresentation() async throws {
        let viewModel = CullingWorkspaceViewModel()
        #expect(!viewModel.isShortcutsHelpPresented)

        viewModel.presentShortcutsHelp()
        #expect(viewModel.isShortcutsHelpPresented)
    }
}
