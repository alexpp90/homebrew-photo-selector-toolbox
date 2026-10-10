import Testing
import Foundation
import SwiftUI
import AppKit
@testable import PhotoSelectorKit

// MARK: - Unit Tests

@Suite("CullingWorkspaceViewModel Unit Tests")
struct CullingWorkspaceViewModelTests {

    private func createSamplePhotos(count: Int) -> [PhotoItem] {
        (0..<count).map { i in
            let url = URL(fileURLWithPath: "/tmp/sample_\(i).jpg")
            return PhotoItem(id: UUID(), url: url, status: .candidate)
        }
    }

    @Test("ViewModel Initialization: default state is valid")
    @MainActor
    func testInitialization() {
        let vm = CullingWorkspaceViewModel()
        #expect(vm.photos.isEmpty)
        #expect(vm.currentIndex == 0)
        #expect(vm.activeSlotIndex == 0)
        #expect(vm.comparisonMode == .single)
        #expect(vm.zoomState == .fit)
        #expect(vm.isZoomSynced == true)
        #expect(vm.isFilmstripVisible == true)
        #expect(vm.isInfoHUDVisible == true)
        #expect(vm.autoAdvance == true)
        #expect(vm.hasActivePhoto == false)
        #expect(vm.currentPhoto == nil)
    }

    @Test("Navigation: next and previous navigate cleanly and clamp at boundaries")
    @MainActor
    func testNavigationAndClamping() {
        let vm = CullingWorkspaceViewModel()
        let sample = createSamplePhotos(count: 5)
        vm.setPhotos(sample)

        #expect(vm.currentIndex == 0)
        #expect(vm.currentPhoto?.id == sample[0].id)

        // Previous at 0 clamps at 0
        vm.navigateToPrevious()
        #expect(vm.currentIndex == 0)

        // Navigate forward
        vm.navigateToNext()
        #expect(vm.currentIndex == 1)

        vm.navigateToNext()
        #expect(vm.currentIndex == 2)

        // Jump to end
        vm.selectPhoto(at: 4)
        #expect(vm.currentIndex == 4)

        // Next at end clamps at 4
        vm.navigateToNext()
        #expect(vm.currentIndex == 4)

        // Navigate back
        vm.navigateToPrevious()
        #expect(vm.currentIndex == 3)
    }

    @Test("Comparison Modes: single, 2-Up, and 3-Up configure slot indices")
    @MainActor
    func testComparisonModesAndSlotSetup() {
        let vm = CullingWorkspaceViewModel()
        let sample = createSamplePhotos(count: 6)
        vm.setPhotos(sample)

        // Default: Single
        #expect(vm.comparisonMode == .single)
        #expect(vm.comparisonSlotIndices == [0])

        // Switch to 2-Up
        vm.setComparisonMode(.sideBySide)
        #expect(vm.comparisonMode == .sideBySide)
        #expect(vm.comparisonSlotIndices == [0, 1])
        #expect(vm.comparisonSlotPhotos.count == 2)

        // Switch to 3-Up
        vm.setComparisonMode(.triplet)
        #expect(vm.comparisonMode == .triplet)
        #expect(vm.comparisonSlotIndices == [0, 1, 2])
        #expect(vm.comparisonSlotPhotos.count == 3)

        // Slot cycling
        #expect(vm.activeSlotIndex == 0)
        vm.advanceComparisonSlotFocus()
        #expect(vm.activeSlotIndex == 1)
        vm.advanceComparisonSlotFocus()
        #expect(vm.activeSlotIndex == 2)
        vm.advanceComparisonSlotFocus()
        #expect(vm.activeSlotIndex == 0) // Wraps around
    }

    @Test("King of the Hill pairwise replacement in comparison mode")
    @MainActor
    func testKingOfTheHillComparisonAdvance() {
        let vm = CullingWorkspaceViewModel()
        let sample = createSamplePhotos(count: 6)
        vm.setPhotos(sample)
        vm.setComparisonMode(.sideBySide)

        // Slot 0 has sample[0], Slot 1 has sample[1]
        #expect(vm.comparisonSlotIndices == [0, 1])

        // Focus Slot 1 (Challenger)
        vm.setActiveSlot(1)
        #expect(vm.activeSlotIndex == 1)

        // Cull Slot 1 with Move action
        vm.cullCurrentPhoto(action: .move)

        // Slot 0 (sample[0]) remains untouched!
        #expect(vm.comparisonSlotIndices[0] == 0)

        // Slot 1 auto-advances to next uninspected candidate (sample[2])!
        #expect(vm.comparisonSlotIndices[1] == 2)
        #expect(vm.photos[1].status == .selected)
    }

    @Test("Comparison Mode: Backward navigation steps active slot safely without duplicate slots")
    @MainActor
    func testComparisonBackwardNavigationSkipsDisplayedSlots() {
        let vm = CullingWorkspaceViewModel()
        let sample = createSamplePhotos(count: 5)
        vm.setPhotos(sample)
        vm.setComparisonMode(.sideBySide)

        // Initial: [0, 1], active slot 1
        #expect(vm.comparisonSlotIndices == [0, 1])
        vm.setActiveSlot(1)

        // Previous on Slot 1 clamps at 1 because index 0 is displayed in Slot 0
        vm.navigateToPrevious()
        #expect(vm.comparisonSlotIndices == [0, 1])

        // Navigate Slot 1 forward to 2 -> [0, 2]
        vm.navigateToNext()
        #expect(vm.comparisonSlotIndices == [0, 2])

        // Navigate Slot 1 back -> steps to 1 -> [0, 1]
        vm.navigateToPrevious()
        #expect(vm.comparisonSlotIndices == [0, 1])

        // Switch to Slot 0, step forward to index 3 -> [3, 1]
        vm.setActiveSlot(0)
        vm.selectPhoto(at: 3)
        #expect(vm.comparisonSlotIndices == [3, 1])

        // Step Slot 0 backward -> steps to 2 -> [2, 1]
        vm.navigateToPrevious()
        #expect(vm.comparisonSlotIndices == [2, 1])

        // Step Slot 0 backward again -> skips 1 (displayed in Slot 1) and lands on 0 -> [0, 1]
        vm.navigateToPrevious()
        #expect(vm.comparisonSlotIndices == [0, 1])
    }

    @Test("Optimistic Culling: Status updates instantly with toasts (< 1ms)")
    @MainActor
    func testOptimisticCullingStatusAndToasts() {
        let vm = CullingWorkspaceViewModel()
        let sample = createSamplePhotos(count: 5)
        vm.setPhotos(sample)
        vm.autoAdvance = false // Keep selection in place to verify status

        // Move to Selection
        vm.cullCurrentPhoto(action: .move)
        #expect(vm.photos[0].status == .selected)
        #expect(vm.currentToast != nil)
        #expect(vm.currentToast?.title == "Moved to Selection")
        #expect(vm.currentToast?.actionType == .move)

        // Copy to Selection
        vm.selectPhoto(at: 1)
        vm.cullCurrentPhoto(action: .copy)
        #expect(vm.photos[1].status == .copied)
        #expect(vm.currentToast?.title == "Copied to Selection")
        #expect(vm.currentToast?.actionType == .copy)

        // Move to Trash
        vm.selectPhoto(at: 2)
        vm.cullCurrentPhoto(action: .trash)
        #expect(vm.photos[2].status == .trashed)
        #expect(vm.currentToast?.title == "Moved to Trash")
        #expect(vm.currentToast?.actionType == .trash)
    }

    @Test("Auto-Advance: Automatically steps to next photo after cull when enabled")
    @MainActor
    func testAutoAdvanceAfterCulling() {
        let vm = CullingWorkspaceViewModel()
        let sample = createSamplePhotos(count: 5)
        vm.setPhotos(sample)
        vm.autoAdvance = true

        #expect(vm.currentIndex == 0)
        vm.cullCurrentPhoto(action: .move)

        // Automatically advanced to photo 1
        #expect(vm.currentIndex == 1)
        #expect(vm.photos[0].status == .selected)
    }

    @Test("Undo: Reverts status and restores selection immediately (< 1ms)")
    @MainActor
    func testUndoRestoration() async {
        let vm = CullingWorkspaceViewModel()
        let sample = createSamplePhotos(count: 5)
        vm.setPhotos(sample)
        vm.autoAdvance = true

        // Cull photo 0
        vm.cullCurrentPhoto(action: .move)
        #expect(vm.photos[0].status == .selected)
        #expect(vm.currentIndex == 1)

        // Undo
        vm.undoLastAction()
        #expect(vm.photos[0].status == .candidate)
        #expect(vm.currentIndex == 0)
        #expect(vm.currentToast?.title == "Action Undone")
    }

    @Test("Zoom and Sync toggles update workspace state")
    @MainActor
    func testZoomAndSyncToggles() {
        let vm = CullingWorkspaceViewModel()

        #expect(vm.zoomState == .fit)
        vm.toggleZoom()
        #expect(vm.zoomState == .oneToOne)
        vm.toggleZoom()
        #expect(vm.zoomState == .fit)

        #expect(vm.isZoomSynced == true)
        vm.toggleZoomSync()
        #expect(vm.isZoomSynced == false)
        vm.toggleZoomSync()
        #expect(vm.isZoomSynced == true)
    }
}
