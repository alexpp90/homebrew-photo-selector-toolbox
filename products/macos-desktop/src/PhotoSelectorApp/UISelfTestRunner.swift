import SwiftUI
import AppKit
import PhotoSelectorKit

/// Live references the self-test needs but the view hierarchy owns.
@MainActor
enum UISelfTestHooks {
    static weak var keyboardRouter: KeyboardShortcutRouter?
}

/// In-app UI self-test (`--ui-self-test <folder>`).
///
/// Why it exists: the keyboard router's unit tests passed for months while the real app
/// ignored every arrow key, because the failure lived outside the router — the process was
/// never allowed to become the active app. This runner exercises the *running* app:
///
/// 1. OS-level keyboard preconditions: activation policy `.regular`, app active, workspace
///    window key, router monitor attached (`ForegroundActivation.canReceiveKeyboard`).
/// 2. Progressive loading: time until the first photo is listed and first drawn.
/// 3. Real `NSEvent` key-downs posted into the app's event queue (→ ← 3 Space Esc), which
///    travel the same path as hardware keys from `NSApplication` onward.
/// 4. Focus 3-Up geometry as rendered: CURRENT above PREVIOUS and NEXT, PREVIOUS left of NEXT.
/// 5. Real mouse clicks on toolbar buttons and the Fit pill while zoomed.
/// 6. Optionally (`--external-key-wait S`): waits for a key delivered by the window server
///    from outside the process (`osascript … key code 124`), proving end-to-end delivery.
@MainActor
final class UISelfTestRunner {
    struct Check: Codable {
        let name: String
        let passed: Bool
        let detail: String
    }

    struct Report: Codable {
        var passed: Bool
        var checks: [Check]
        var timings: [String: Double]
        var externalKey: String
    }

    private let viewModel: CullingWorkspaceViewModel
    private let folder: URL
    private let reportURL: URL?
    private let expectedCount: Int?
    private let externalKeyWait: TimeInterval
    private var checks: [Check] = []
    private var timings: [String: Double] = [:]
    private var externalKey = "not-requested"

    init(viewModel: CullingWorkspaceViewModel, folder: URL, reportURL: URL?, expectedCount: Int?, externalKeyWait: TimeInterval) {
        self.viewModel = viewModel
        self.folder = folder
        self.reportURL = reportURL
        self.expectedCount = expectedCount
        self.externalKeyWait = externalKeyWait
    }

    // MARK: - Run

    func run() async -> Bool {
        await waitUntil(timeout: 5) { self.workspaceWindow != nil && NSApp.isActive }
        await activationChecks()
        await loadingChecks()
        guard viewModel.photos.count >= 3 else {
            record("fixture.has_three_photos", false, "need ≥3 photos, found \(viewModel.photos.count)")
            return finish()
        }
        await keyboardChecks()
        await tripletLayoutChecks()
        await zoomChecks()
        if externalKeyWait > 0 {
            await externalKeyCheck()
        }
        return finish()
    }

    // MARK: - 1. Activation

    private func activationChecks() async {
        let policy = NSApp.activationPolicy()
        record("activation.policy_regular", policy == .regular, "policy=\(policy.rawValue) (0 = regular)")
        record("activation.app_active", NSApp.isActive, "NSApp.isActive=\(NSApp.isActive)")
        let window = workspaceWindow
        record("activation.workspace_window_key", window?.isKeyWindow == true, "keyWindow=\(String(describing: NSApp.keyWindow?.title))")
        await waitUntil(timeout: 2) { UISelfTestHooks.keyboardRouter?.isAttached == true }
        record("keyboard.router_attached", UISelfTestHooks.keyboardRouter?.isAttached == true, "local key monitor registered")
        record(
            "keyboard.os_delivers_keys",
            ForegroundActivation.canReceiveKeyboard(policy: policy, isActive: NSApp.isActive, hasKeyWindow: window?.isKeyWindow == true),
            "all three OS preconditions for key delivery hold"
        )
    }

    // MARK: - 2. Progressive loading

    private func loadingChecks() async {
        DisplayTelemetry.shared.reset()
        let start = Date()
        viewModel.loadFolder(at: folder)

        await waitUntil(timeout: 20) { !self.viewModel.photos.isEmpty }
        let listed = Date().timeIntervalSince(start)
        timings["first_photo_listed_s"] = listed

        await waitUntil(timeout: 20) {
            guard let first = self.viewModel.photos.first else { return false }
            return DisplayTelemetry.shared.firstDisplay(of: first.id, tier: .quick) != nil
                || DisplayTelemetry.shared.firstDisplay(of: first.id, tier: .preview) != nil
        }
        if let first = viewModel.photos.first {
            let shown = [ThumbnailTier.quick, .preview]
                .compactMap { DisplayTelemetry.shared.firstDisplay(of: first.id, tier: $0) }
                .min()
            if let shown {
                timings["first_photo_drawn_s"] = shown.timeIntervalSince(start)
            }
            record("loading.first_photo_drawn", shown != nil, "first photo drawn before the scan completed or within 20 s")
        }
        timings["scan_still_running_when_first_drawn"] = viewModel.isLoading ? 1 : 0

        await waitUntil(timeout: 120) { !self.viewModel.isLoading }
        timings["scan_complete_s"] = Date().timeIntervalSince(start)
        if let expectedCount {
            record("loading.all_photos_listed", viewModel.photos.count == expectedCount, "listed \(viewModel.photos.count), expected \(expectedCount)")
        }
        if let drawn = timings["first_photo_drawn_s"] {
            record("loading.first_photo_under_2s", drawn < 2.0, String(format: "first photo drawn after %.3f s", drawn))
        }
    }

    // MARK: - 3. Keyboard through the real event queue

    private func keyboardChecks() async {
        viewModel.setComparisonMode(.single)
        viewModel.selectPhoto(at: 0)
        _ = viewModel.resetZoomToFit()
        await settle()

        await postKey(.rightArrow)
        await waitUntil(timeout: 1) { self.viewModel.currentIndex == 1 }
        record("keyboard.right_arrow_next", viewModel.currentIndex == 1, "currentIndex=\(viewModel.currentIndex) after →")

        await postKey(.leftArrow)
        await waitUntil(timeout: 1) { self.viewModel.currentIndex == 0 }
        record("keyboard.left_arrow_previous", viewModel.currentIndex == 0, "currentIndex=\(viewModel.currentIndex) after ←")

        await postKey(.downArrow)
        await waitUntil(timeout: 1) { self.viewModel.currentIndex == 1 }
        record("keyboard.down_arrow_next", viewModel.currentIndex == 1, "currentIndex=\(viewModel.currentIndex) after ↓")

        await postKey(.three)
        await waitUntil(timeout: 1) { self.viewModel.comparisonMode == .triplet }
        record("keyboard.3_focus_triplet", viewModel.comparisonMode == .triplet, "mode=\(viewModel.comparisonMode)")
    }

    // MARK: - 4. Focus 3-Up geometry

    private func tripletLayoutChecks() async {
        if viewModel.comparisonMode != .triplet { viewModel.setComparisonMode(.triplet) }
        if viewModel.currentIndex == 0 { viewModel.selectPhoto(at: 1) }
        let registry = UITestFrameRegistry.shared
        await waitUntil(timeout: 3) {
            registry.frame("slot_current") != nil && registry.frame("slot_previous") != nil && registry.frame("slot_next") != nil
        }
        guard let current = registry.frame("slot_current"),
              let previous = registry.frame("slot_previous"),
              let next = registry.frame("slot_next") else {
            record("layout.triplet_panes_rendered", false, "frames: \(registry.frames.keys.sorted())")
            return
        }
        // `.global` space is y-down: smaller minY is higher on screen.
        let tolerance: CGFloat = 1
        record("layout.current_above_previous", current.maxY <= previous.minY + tolerance, "current=\(current) previous=\(previous)")
        record("layout.current_above_next", current.maxY <= next.minY + tolerance, "current=\(current) next=\(next)")
        record("layout.previous_left_of_next", previous.maxX <= next.minX + tolerance, "previous=\(previous) next=\(next)")
        record("layout.current_spans_width", current.width >= previous.width + next.width - tolerance, "current.width=\(current.width)")
        record(
            "layout.current_largest",
            current.width * current.height > previous.width * previous.height,
            "current area \(Int(current.width * current.height)) vs previous \(Int(previous.width * previous.height))"
        )
    }

    // MARK: - 5. Zoom: exits and buttons while zoomed

    private func zoomChecks() async {
        viewModel.setComparisonMode(.single)
        viewModel.selectPhoto(at: 0)
        await settle()

        await postKey(.space)
        await waitUntil(timeout: 1) { self.viewModel.zoomState == .oneToOne }
        record("zoom.space_enters_100", viewModel.zoomState == .oneToOne, "zoom=\(viewModel.zoomState)")

        await postKey(.escape)
        await waitUntil(timeout: 1) { self.viewModel.zoomState == .fit }
        record("zoom.escape_exits_100", viewModel.zoomState == .fit, "zoom=\(viewModel.zoomState)")

        viewModel.setPinchMagnification(2.5)
        await settle()
        await postKey(.escape)
        await waitUntil(timeout: 1) { self.viewModel.zoomState == .fit }
        record("zoom.escape_exits_pinch", viewModel.zoomState == .fit, "zoom=\(viewModel.zoomState)")

        // Buttons must stay clickable while zoomed.
        viewModel.toggleZoom()
        await settle()
        let before = viewModel.currentIndex
        let clickedNext = await click("toolbar_next")
        await waitUntil(timeout: 1) { self.viewModel.currentIndex == before + 1 }
        record("zoom.toolbar_next_clickable_while_zoomed", clickedNext && viewModel.currentIndex == before + 1, "index \(before)→\(viewModel.currentIndex)")

        if !viewModel.zoomState.isZoomed { viewModel.toggleZoom(); await settle() }
        let clickedPill = await click("zoom_exit_button")
        await waitUntil(timeout: 1) { self.viewModel.zoomState == .fit }
        record("zoom.fit_pill_exits", clickedPill && viewModel.zoomState == .fit, "zoom=\(viewModel.zoomState)")

        viewModel.setPinchMagnification(3)
        await settle()
        let clickedZoom = await click("toolbar_zoom")
        await waitUntil(timeout: 1) { self.viewModel.zoomState == .fit }
        record("zoom.toolbar_button_exits_pinch", clickedZoom && viewModel.zoomState == .fit, "zoom=\(viewModel.zoomState)")

        viewModel.toggleZoom()
        await settle()
        let clickedMode = await click("toolbar_mode_3")
        await waitUntil(timeout: 1) { self.viewModel.comparisonMode == .triplet }
        record("zoom.mode_button_clickable_while_zoomed", clickedMode && viewModel.comparisonMode == .triplet, "mode=\(viewModel.comparisonMode)")
        _ = viewModel.resetZoomToFit()
        viewModel.setComparisonMode(.single)
    }

    // MARK: - 6. External key through the window server

    private func externalKeyCheck() async {
        viewModel.setComparisonMode(.single)
        viewModel.selectPhoto(at: 0)
        Self.writeMarker(reportURL, suffix: ".ready")
        let ok = await waitUntil(timeout: externalKeyWait) { self.viewModel.currentIndex != 0 }
        externalKey = ok ? "delivered" : "not-observed"
    }

    // MARK: - Event injection

    enum Key {
        case rightArrow, leftArrow, downArrow, space, escape, three

        var keyCode: UInt16 {
            switch self {
            case .rightArrow: return 124
            case .leftArrow: return 123
            case .downArrow: return 125
            case .space: return 49
            case .escape: return 53
            case .three: return 20
            }
        }

        var characters: String {
            switch self {
            case .rightArrow: return String(Character(UnicodeScalar(0xF703)!))
            case .leftArrow: return String(Character(UnicodeScalar(0xF702)!))
            case .downArrow: return String(Character(UnicodeScalar(0xF701)!))
            case .space: return " "
            case .escape: return "\u{1B}"
            case .three: return "3"
            }
        }

        var modifiers: NSEvent.ModifierFlags {
            switch self {
            case .rightArrow, .leftArrow, .downArrow: return [.numericPad, .function]
            default: return []
            }
        }
    }

    private func postKey(_ key: Key) async {
        guard let window = workspaceWindow else { return }
        for type in [NSEvent.EventType.keyDown, .keyUp] {
            if let event = NSEvent.keyEvent(
                with: type,
                location: .zero,
                modifierFlags: key.modifiers,
                timestamp: ProcessInfo.processInfo.systemUptime,
                windowNumber: window.windowNumber,
                context: nil,
                characters: key.characters,
                charactersIgnoringModifiers: key.characters,
                isARepeat: false,
                keyCode: key.keyCode
            ) {
                NSApp.postEvent(event, atStart: false)
            }
        }
        await settle()
    }

    /// Posts a left click at the centre of a registered view. Returns `false` if the view
    /// is not on screen.
    private func click(_ identifier: String) async -> Bool {
        await waitUntil(timeout: 2) { UITestFrameRegistry.shared.frame(identifier) != nil }
        guard let window = workspaceWindow,
              let frame = UITestFrameRegistry.shared.frame(identifier),
              let contentView = window.contentView else { return false }
        // SwiftUI `.global` is relative to the hosting view, y-down; window coordinates are y-up.
        let point = CGPoint(x: frame.midX, y: contentView.frame.height - frame.midY)
        for type in [NSEvent.EventType.leftMouseDown, .leftMouseUp] {
            if let event = NSEvent.mouseEvent(
                with: type,
                location: point,
                modifierFlags: [],
                timestamp: ProcessInfo.processInfo.systemUptime,
                windowNumber: window.windowNumber,
                context: nil,
                eventNumber: 0,
                clickCount: 1,
                pressure: type == .leftMouseDown ? 1 : 0
            ) {
                NSApp.postEvent(event, atStart: false)
            }
        }
        await settle()
        return true
    }

    // MARK: - Helpers

    private var workspaceWindow: NSWindow? {
        NSApp.windows.first { $0.isVisible && $0.canBecomeKey && !$0.isSheet && $0.contentView != nil }
    }

    private func record(_ name: String, _ passed: Bool, _ detail: String) {
        checks.append(Check(name: name, passed: passed, detail: detail))
        FileHandle.standardError.write(Data("[ui-self-test] \(passed ? "PASS" : "FAIL") \(name) — \(detail)\n".utf8))
    }

    private func settle() async {
        try? await Task.sleep(nanoseconds: 150_000_000)
    }

    @discardableResult
    private func waitUntil(timeout: TimeInterval, _ condition: @MainActor () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return condition()
    }

    private func finish() -> Bool {
        let passed = checks.allSatisfy(\.passed) && !checks.isEmpty
        let report = Report(passed: passed, checks: checks, timings: timings, externalKey: externalKey)
        if let reportURL, let data = try? JSONEncoder.pretty.encode(report) {
            try? data.write(to: reportURL)
        }
        return passed
    }

    private static func writeMarker(_ reportURL: URL?, suffix: String) {
        guard let reportURL else { return }
        let marker = URL(fileURLWithPath: reportURL.path + suffix)
        try? Data().write(to: marker)
    }
}

private extension JSONEncoder {
    static var pretty: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }
}
