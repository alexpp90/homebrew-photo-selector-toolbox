import Testing
import AppKit
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
@testable import PhotoSelectorKit

// Regression tests for the October 2026 macOS fixes: progressive loading, keyboard
// delivery, Focus 3-Up one-over-two layout, and zoom exits. The end-to-end proof that the
// *running* app behaves lives in `scripts/ui_smoke_test.sh`; these pin the rules.

// MARK: - Focus 3-Up layout (REQ-MAC-LAYOUT.02)

@Suite("FocusTripletLayout: current on top, previous and next below")
struct FocusTripletLayoutTests {
    static let canvases: [CGSize] = [
        CGSize(width: 1272, height: 640),   // default window canvas
        CGSize(width: 1920, height: 1000),  // large display
        CGSize(width: 952, height: 420),    // minimum window
    ]

    @Test("REQ-MAC-LAYOUT.02: current spans the full width above previous and next", arguments: canvases)
    func currentOnTop(canvas: CGSize) {
        let layout = FocusTripletLayout(canvas: canvas)
        #expect(layout.current.minY == 0)
        #expect(layout.current.width == canvas.width)
        #expect(layout.current.maxY <= layout.previous.minY)
        #expect(layout.current.maxY <= layout.next.minY)
        #expect(layout.previous.minY == layout.next.minY, "previous and next share one row")
    }

    @Test("REQ-MAC-LAYOUT.02: previous is bottom-left, next is bottom-right, no overlap", arguments: canvases)
    func chronologicalBottomRow(canvas: CGSize) {
        let layout = FocusTripletLayout(canvas: canvas)
        #expect(layout.previous.minX == 0)
        #expect(layout.previous.maxX <= layout.next.minX)
        #expect(!layout.previous.intersects(layout.next))
        #expect(!layout.current.intersects(layout.previous))
        #expect(!layout.current.intersects(layout.next))
        let bounds = CGRect(origin: .zero, size: canvas)
        for rect in [layout.current, layout.previous, layout.next] {
            #expect(bounds.contains(rect), "\(rect) must stay inside \(bounds)")
        }
    }

    @Test("REQ-MAC-LAYOUT.02: a landscape current photo is shown larger than in three columns", arguments: canvases)
    func landscapeCurrentIsLargest(canvas: CGSize) {
        let layout = FocusTripletLayout(canvas: canvas)
        let landscape: CGFloat = 3.0 / 2.0
        let currentArea = FocusTripletLayout.fittedArea(aspectRatio: landscape, in: layout.current)
        let neighbourArea = FocusTripletLayout.fittedArea(aspectRatio: landscape, in: layout.previous)
        #expect(currentArea > neighbourArea * 1.5)

        // The rejected three-column layout: each column a third of the width.
        let column = CGRect(x: 0, y: 0, width: (canvas.width - 12) / 3, height: canvas.height)
        let columnArea = FocusTripletLayout.fittedArea(aspectRatio: landscape, in: column)
        #expect(currentArea > columnArea, "one-over-two must beat three columns for landscape photos")
    }

    @Test("Row share is clamped so neighbours stay readable")
    func rowShareClamped() {
        let layout = FocusTripletLayout(canvas: CGSize(width: 1000, height: 606), spacing: 6, currentRowShare: 0.99)
        #expect(layout.previous.height >= 600 * 0.2 - 1)
    }
}

// MARK: - Zoom geometry and state machine (REQ-MAC-ZOOM.01)

@Suite("REQ-MAC-ZOOM.01: honest 100 %, clamped pan, and every exit back to Fit")
@MainActor
struct ZoomBehaviourTests {
    private func makeViewModel(count: Int = 5) -> CullingWorkspaceViewModel {
        let vm = CullingWorkspaceViewModel()
        vm.setPhotos((0..<count).map { PhotoItem(id: UUID(), url: URL(fileURLWithPath: "/tmp/zoom_\($0).jpg"), status: .candidate) })
        return vm
    }

    @Test("REQ-MAC-ZOOM.01: 100 % maps one original pixel to one device pixel")
    func honestOneToOne() {
        // 6000 px wide photo fitted into a 1200 pt viewport on a 2× display → 2.5×.
        let scale = ZoomGeometry.oneToOneScale(
            originalPixelSize: CGSize(width: 6000, height: 4000),
            viewport: CGSize(width: 1200, height: 900),
            backingScale: 2
        )
        #expect(abs(scale - 2.5) < 0.0001)
        // A photo smaller than the viewport is never shrunk below Fit.
        #expect(ZoomGeometry.oneToOneScale(originalPixelSize: CGSize(width: 800, height: 600),
                                           viewport: CGSize(width: 1200, height: 900), backingScale: 2) == 1)
    }

    @Test("Pan is clamped so the photo always covers the viewport")
    func clampedPan() {
        let pan = ZoomGeometry.clampedPan(CGSize(width: 10_000, height: -10_000), scale: 2,
                                          fittedSize: CGSize(width: 1200, height: 800), viewport: CGSize(width: 1200, height: 900))
        #expect(pan.width == 600)   // (2400 - 1200) / 2
        #expect(pan.height == -350) // (1600 - 900) / 2
        #expect(ZoomGeometry.clampedPan(CGSize(width: 50, height: 50), scale: 1,
                                        fittedSize: CGSize(width: 1200, height: 800), viewport: CGSize(width: 1200, height: 900)) == .zero)
    }

    @Test("Space toggles Fit ↔ 100 %, and from a pinch zoom returns to Fit")
    func toggleFromAnyZoom() {
        let vm = makeViewModel()
        vm.toggleZoom()
        #expect(vm.zoomState == .oneToOne)
        vm.toggleZoom()
        #expect(vm.zoomState == .fit)
        vm.setPinchMagnification(3)
        #expect(vm.zoomState == .magnified(3))
        vm.toggleZoom()
        #expect(vm.zoomState == .fit, "Space / the toolbar button must leave a pinch zoom, not jump to 100 %")
    }

    @Test("REQ-MAC-ZOOM.01, REQ-MAC-KEY.01: Escape leaves 100 % and pinch zoom; reports false when nothing was zoomed")
    func escapeExits() {
        let vm = makeViewModel()
        #expect(vm.resetZoomToFit() == false)
        vm.toggleZoom()
        #expect(vm.resetZoomToFit() == true)
        #expect(vm.zoomState == .fit)
        vm.setPinchMagnification(2)
        #expect(vm.resetZoomToFit() == true)
        #expect(vm.zoomState == .fit)
    }

    @Test("A pinch that ends near 1× snaps back to Fit; large pinches are capped")
    func pinchSnapAndCap() {
        let vm = makeViewModel()
        vm.setPinchMagnification(1.01)
        #expect(vm.zoomState == .fit)
        vm.setPinchMagnification(0.4)
        #expect(vm.zoomState == .fit)
        vm.setPinchMagnification(50)
        #expect(vm.zoomState == .magnified(8))
    }

    @Test("Navigation stays possible while zoomed")
    func navigationWhileZoomed() {
        let vm = makeViewModel()
        vm.toggleZoom()
        vm.navigateToNext()
        #expect(vm.currentIndex == 1)
        vm.setComparisonMode(.triplet)
        #expect(vm.comparisonMode == .triplet)
    }

    @Test("Escape key event routes to resetZoomToFit and is not consumed when not zoomed")
    func escapeKeyRouting() {
        let vm = makeViewModel()
        let router = KeyboardShortcutRouter()
        let escape = NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [], timestamp: 0,
                                      windowNumber: 0, context: nil, characters: "\u{1B}",
                                      charactersIgnoringModifiers: "\u{1B}", isARepeat: false, keyCode: 53)!
        #expect(router.handleKeyEvent(escape, viewModel: vm) == false, "unzoomed Esc must reach sheets / system")
        vm.setPinchMagnification(2.5)
        #expect(router.handleKeyEvent(escape, viewModel: vm) == true)
        #expect(vm.zoomState == .fit)
    }
}

// MARK: - Keyboard delivery preconditions (REQ-MAC-KEY.01)

@MainActor
private final class FakeActivationController: ActivationPolicyControlling {
    var policy: NSApplication.ActivationPolicy
    var setCalls = 0
    init(policy: NSApplication.ActivationPolicy) { self.policy = policy }
    func activationPolicy() -> NSApplication.ActivationPolicy { policy }
    func setActivationPolicy(_ activationPolicy: NSApplication.ActivationPolicy) -> Bool {
        setCalls += 1
        policy = activationPolicy
        return true
    }
}

@Suite("ForegroundActivation: the app must be allowed to receive keys")
@MainActor
struct ForegroundActivationTests {
    @Test("REQ-MAC-KEY.01: an unbundled launch (.prohibited) is promoted to .regular")
    func promotesProhibited() {
        let app = FakeActivationController(policy: .prohibited)
        #expect(ForegroundActivation.ensureKeyboardCapable(app) == true)
        #expect(app.policy == .regular)
    }

    @Test("Accessory launches are promoted too; regular launches are left alone")
    func leavesRegularAlone() {
        let accessory = FakeActivationController(policy: .accessory)
        ForegroundActivation.ensureKeyboardCapable(accessory)
        #expect(accessory.policy == .regular)

        let regular = FakeActivationController(policy: .regular)
        #expect(ForegroundActivation.ensureKeyboardCapable(regular) == false)
        #expect(regular.setCalls == 0)
    }

    @Test("Key delivery needs regular policy, an active app and a key window")
    func keyboardPreconditions() {
        #expect(ForegroundActivation.canReceiveKeyboard(policy: .regular, isActive: true, hasKeyWindow: true))
        #expect(!ForegroundActivation.canReceiveKeyboard(policy: .prohibited, isActive: true, hasKeyWindow: true))
        #expect(!ForegroundActivation.canReceiveKeyboard(policy: .regular, isActive: false, hasKeyWindow: true))
        #expect(!ForegroundActivation.canReceiveKeyboard(policy: .regular, isActive: true, hasKeyWindow: false))
    }

    @Test("Sheet windows own the keyboard: arrows do not move the workspace behind them")
    func sheetGuard() {
        let plain = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 200, height: 200), styleMask: [.titled], backing: .buffered, defer: true)
        let presenting = PresentingWindow(contentRect: NSRect(x: 0, y: 0, width: 200, height: 200), styleMask: [.titled], backing: .buffered, defer: true)
        let sheet = SheetWindow(contentRect: NSRect(x: 0, y: 0, width: 100, height: 100), styleMask: [.titled], backing: .buffered, defer: true)
        #expect(KeyboardShortcutRouter.isSheetContext(plain) == false)
        #expect(KeyboardShortcutRouter.isSheetContext(presenting) == true, "a window presenting a sheet")
        #expect(KeyboardShortcutRouter.isSheetContext(sheet) == true, "the sheet itself")

        let vm = CullingWorkspaceViewModel()
        vm.setPhotos((0..<3).map { PhotoItem(id: UUID(), url: URL(fileURLWithPath: "/tmp/s_\($0).jpg"), status: .candidate) })
        let arrow = NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [.numericPad, .function], timestamp: 0,
                                     windowNumber: 0, context: nil, characters: "\u{F703}",
                                     charactersIgnoringModifiers: "\u{F703}", isARepeat: false, keyCode: 124)!
        // windowNumber 0 → no window: the workspace path still navigates.
        #expect(KeyboardShortcutRouter().handleKeyEvent(arrow, viewModel: vm) == true)
        #expect(vm.currentIndex == 1)
    }
}

private final class PresentingWindow: NSWindow {
    private let fakeSheet = NSWindow()
    override var attachedSheet: NSWindow? { fakeSheet }
}

private final class SheetWindow: NSWindow {
    override var isSheet: Bool { true }
}

// MARK: - Progressive loading (REQ-MAC-CULL.01 / .02)

@Suite("Progressive loading: first photo first, no per-file work before it")
struct ProgressiveLoadingTests {
    private func tempDir(_ prefix: String) throws -> URL {
        let base = FileManager.default.temporaryDirectory.standardizedFileURL.resolvingSymlinksInPath()
        let dir = base.appendingPathComponent("\(prefix)_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let canonical = (try? dir.resourceValues(forKeys: [.canonicalPathKey]))?.canonicalPath ?? dir.path
        return URL(fileURLWithPath: canonical, isDirectory: true)
    }

    private func item(_ i: Int) -> PhotoItem {
        PhotoItem(id: UUID(), url: URL(fileURLWithPath: "/tmp/b_\(i).jpg"), status: .candidate)
    }

    @Test("REQ-MAC-CULL.01: batches ramp 1 → 4 → 16 → batchSize")
    func batcherRamp() {
        var batcher = ScanBatcher(firstBatchSize: 1, batchSize: 30)
        var sizes: [Int] = []
        for i in 0..<100 {
            if let batch = batcher.append(item(i)) { sizes.append(batch.count) }
        }
        if let rest = batcher.flush() { sizes.append(rest.count) }
        #expect(sizes == [1, 4, 16, 30, 30, 19])
        #expect(sizes.reduce(0, +) == 100)
    }

    @Test("REQ-MAC-CULL.01: the first streamed batch holds exactly one photo")
    func firstBatchIsOnePhoto() async throws {
        let root = try tempDir("FirstBatch")
        defer { try? FileManager.default.removeItem(at: root) }
        for i in 1...60 {
            try Data("x".utf8).write(to: root.appendingPathComponent(String(format: "IMG_%04d.JPG", i)))
        }
        var sizes: [Int] = []
        for await batch in DirectoryScanner().scanStream(at: root, recursive: false, batchSize: 30, firstBatchSize: 1) {
            sizes.append(batch.count)
        }
        #expect(sizes.first == 1)
        #expect(sizes.reduce(0, +) == 60)
    }

    @Test("Natural order across a folder: IMG_2 before IMG_10")
    func naturalOrder() async throws {
        let root = try tempDir("Natural")
        defer { try? FileManager.default.removeItem(at: root) }
        for name in ["IMG_10.JPG", "IMG_2.JPG", "IMG_1.JPG"] {
            try Data("x".utf8).write(to: root.appendingPathComponent(name))
        }
        let items = try await DirectoryScanner().scanDirectory(at: root, recursive: false)
        #expect(items.map { $0.url.lastPathComponent } == ["IMG_1.JPG", "IMG_2.JPG", "IMG_10.JPG"])
    }

    @Test("REQ-MAC-EXIF.06: PhotoTok and selection output folders are never scanned")
    func excludedFolders() async throws {
        let root = try tempDir("Excluded")
        defer { try? FileManager.default.removeItem(at: root) }
        try Data("x".utf8).write(to: root.appendingPathComponent("KEEP.JPG"))
        for folder in ["Selection", "selected", "phototok_selection", "PhotoTok_LeftSwipe"] {
            let dir = root.appendingPathComponent(folder, isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try Data("x".utf8).write(to: dir.appendingPathComponent("SKIP.JPG"))
        }
        let items = try await DirectoryScanner().scanDirectory(at: root, recursive: true)
        #expect(items.map { $0.url.lastPathComponent } == ["KEEP.JPG"])
    }

    @Test("In-memory grouping: RAW wins over JPEG, edits group with their capture")
    func grouping() {
        let dir = URL(fileURLWithPath: "/tmp/group", isDirectory: true)
        let files = ["IMG_0001.JPG", "IMG_0001.CR3", "IMG_0001-Edit.jpg", "IMG_0002.JPG", "IMG_0002.xmp"]
            .map { dir.appendingPathComponent($0) }
        let primaries = DirectoryScanner.groupPrimaryPhotos(in: files).map { $0.lastPathComponent }
        #expect(primaries == ["IMG_0001.CR3", "IMG_0002.JPG"])
    }

    @Test("Decode policy: small tiers and RAW previews use the embedded preview; JPEG previews decode")
    func decodePolicy() {
        let jpg = URL(fileURLWithPath: "/tmp/a.jpg")
        let raw = URL(fileURLWithPath: "/tmp/a.cr3")
        #expect(ThumbnailDecodeStrategy.forRequest(url: jpg, maxPixelSize: ThumbnailTier.quick.rawValue) == .embeddedPreferred(minimumLongEdge: 120))
        #expect(ThumbnailDecodeStrategy.forRequest(url: raw, maxPixelSize: ThumbnailTier.preview.rawValue) == .embeddedPreferred(minimumLongEdge: 1000))
        #expect(ThumbnailDecodeStrategy.forRequest(url: jpg, maxPixelSize: ThumbnailTier.preview.rawValue) == .fullDecode)
    }

    @Test("Embedded-preferred decode falls back to a full decode when no usable thumbnail exists")
    func embeddedFallback() throws {
        let root = try tempDir("Embedded")
        defer { try? FileManager.default.removeItem(at: root) }
        let url = root.appendingPathComponent("plain.jpg")
        let ctx = CGContext(data: nil, width: 1200, height: 800, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        ctx.setFillColor(CGColor(red: 1, green: 0, blue: 0, alpha: 1))
        ctx.fill(CGRect(x: 0, y: 0, width: 1200, height: 800))
        let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
        #expect(CGImageDestinationFinalize(dest))

        let image = ThumbnailLoader.decodeThumbnail(from: url, maxPixelSize: 320, strategy: .embeddedPreferred(minimumLongEdge: 120))
        #expect(image != nil)
        #expect(max(image?.width ?? 0, image?.height ?? 0) == 320)
        #expect(ThumbnailLoader.originalPixelSize(of: url) == CGSize(width: 1200, height: 800))
    }

    @Test("REQ-MAC-CULL.02: the progressive stream ends with the full preview tier")
    func progressiveStreamEndsWithPreview() async throws {
        let root = try tempDir("Progressive")
        defer { try? FileManager.default.removeItem(at: root) }
        let url = root.appendingPathComponent("p.jpg")
        let ctx = CGContext(data: nil, width: 3000, height: 2000, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        ctx.setFillColor(CGColor(red: 0, green: 0.5, blue: 1, alpha: 1))
        ctx.fill(CGRect(x: 0, y: 0, width: 3000, height: 2000))
        let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
        #expect(CGImageDestinationFinalize(dest))

        var tiers: [ThumbnailTier] = []
        for await image in ThumbnailLoader().progressivePreview(for: url) {
            tiers.append(image.tier)
        }
        #expect(tiers.last == .preview)
        #expect(tiers.first == .quick || tiers == [.preview])
    }
}
