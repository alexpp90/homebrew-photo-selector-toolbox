import SwiftUI
import AppKit

/// Dedicated window-level keyboard event interceptor.
/// Uses `NSEvent.addLocalMonitorForEvents(matching: .keyDown)` to capture navigation
/// and culling hotkeys (←, →, ↑, ↓, M, C, Delete, Space, 1, 2, 3, Tab, ⌘Z, J, K) before SwiftUI focus
/// or button responders can swallow them.
@MainActor
public final class KeyboardShortcutRouter: ObservableObject {
    private var monitor: Any?
    private weak var viewModel: CullingWorkspaceViewModel?

    public init() {}

    /// Registers the local key-down event monitor.
    public func attach(viewModel: CullingWorkspaceViewModel) {
        self.viewModel = viewModel
        guard monitor == nil else { return }

        monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            guard let self, let vm = self.viewModel else { return event }
            if self.handleKeyEvent(event, viewModel: vm) {
                return nil // Event handled and consumed; prevent button focus routing
            }
            return event
        }
    }

    /// Unregisters the local key-down event monitor.
    public func detach() {
        if let monitor {
            NSEvent.removeMonitor(monitor)
            self.monitor = nil
        }
        self.viewModel = nil
    }

    /// Whether a live key-down event monitor is registered (asserted by the UI self-test).
    public var isAttached: Bool { monitor != nil }

    /// `true` when the window is a sheet, or a window currently presenting a sheet.
    public static func isSheetContext(_ window: NSWindow) -> Bool {
        window.isSheet || window.sheetParent != nil || window.attachedSheet != nil
    }

    /// Evaluates whether an `NSEvent` corresponds to a culling or navigation shortcut.
    /// Returns `true` if handled, or `false` to let the event proceed to standard responders.
    public func handleKeyEvent(_ event: NSEvent, viewModel: CullingWorkspaceViewModel) -> Bool {
        // Do not intercept keystrokes if the user is typing into an editable text field
        let window = event.window ?? NSApp?.keyWindow
        if let window, let firstResponder = window.firstResponder {
            if firstResponder is NSTextView || firstResponder is NSTextField {
                return false
            }
        }

        // Sheets (Guide, Duplicate Finder, Statistics) own the keyboard while presented:
        // arrows must not move the workspace hidden behind them.
        if let window = event.window, Self.isSheetContext(window) {
            return false
        }

        let isCmd = event.modifierFlags.contains(.command)
        let characters = event.charactersIgnoringModifiers?.lowercased() ?? ""

        // 1. Command Shortcuts (⌘Z, ⌘O, ⌘D, ⌘L, ⌘?, ⌘[, ⌘])
        if isCmd {
            switch characters {
            case "z":
                viewModel.undoLastAction()
                return true
            case "o":
                viewModel.presentFolderPicker()
                return true
            case "d":
                viewModel.presentDuplicateFinder()
                return true
            case "l":
                viewModel.presentLibraryStatistics()
                return true
            case "?", "/":
                viewModel.presentShortcutsHelp()
                return true
            case "[":
                viewModel.navigateToPrevious()
                return true
            case "]":
                viewModel.navigateToNext()
                return true
            default:
                return false
            }
        }

        // 2. Navigation Keys (Arrow keys, Space, Delete, Tab, 1, 2, 3, J, K, H, L)
        let isLeft: Bool = {
            if event.keyCode == 123 || event.keyCode == 126 { return true }
            if event.specialKey == .leftArrow || event.specialKey == .upArrow { return true }
            if let chars = event.charactersIgnoringModifiers,
               let scalar = chars.unicodeScalars.first?.value,
               scalar == 0xF702 || scalar == 0xF700 { return true }
            return false
        }()

        let isRight: Bool = {
            if event.keyCode == 124 || event.keyCode == 125 { return true }
            if event.specialKey == .rightArrow || event.specialKey == .downArrow { return true }
            if let chars = event.charactersIgnoringModifiers,
               let scalar = chars.unicodeScalars.first?.value,
               scalar == 0xF703 || scalar == 0xF701 { return true }
            return false
        }()

        let isDelete = event.keyCode == 51 || event.keyCode == 117 || event.specialKey == .delete || event.specialKey == .deleteForward

        if isLeft {
            viewModel.navigateToPrevious()
            return true
        }

        if isRight {
            viewModel.navigateToNext()
            return true
        }

        if isDelete {
            viewModel.cullCurrentPhoto(action: .trash)
            return true
        }

        switch event.keyCode {
        case 49: // Spacebar -> Toggle 100% Zoom
            viewModel.toggleZoom()
            return true
        case 48: // Tab -> Advance / Next
            viewModel.advanceComparisonSlotFocus()
            return true
        case 53: // Escape -> Leave any zoom (100 % or pinch) back to Fit
            return viewModel.resetZoomToFit()
        case 18: // Number 1 -> 1-Up Single
            viewModel.setComparisonMode(.single)
            return true
        case 19: // Number 2 -> 2-Up Side-by-Side
            viewModel.setComparisonMode(.sideBySide)
            return true
        case 20: // Number 3 -> 3-Up Focus Mode
            viewModel.setComparisonMode(.triplet)
            return true
        default:
            break
        }

        // 3. Single-Character Action Shortcuts (M, C, F, I, S, J, K, H, L, ?, /)
        switch characters {
        case "m":
            viewModel.cullCurrentPhoto(action: .move)
            return true
        case "c":
            viewModel.cullCurrentPhoto(action: .copy)
            return true
        case "j", "l": // Vim Down / Right -> Next
            viewModel.navigateToNext()
            return true
        case "k", "h": // Vim Up / Left -> Previous
            viewModel.navigateToPrevious()
            return true
        case "f":
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                viewModel.isFilmstripVisible.toggle()
            }
            return true
        case "i":
            withAnimation(.easeInOut(duration: 0.18)) {
                viewModel.isInfoHUDVisible.toggle()
            }
            return true
        case "s":
            viewModel.toggleZoomSync()
            return true
        case "?", "/":
            viewModel.presentShortcutsHelp()
            return true
        default:
            return false
        }
    }
}
