import AppKit

/// Abstraction over `NSApplication`'s activation policy so the keyboard-capability rule is
/// unit-testable without turning the test runner into a Dock app.
@MainActor
public protocol ActivationPolicyControlling: AnyObject {
    func activationPolicy() -> NSApplication.ActivationPolicy
    func setActivationPolicy(_ activationPolicy: NSApplication.ActivationPolicy) -> Bool
}

extension NSApplication: ActivationPolicyControlling {}

/// Guarantees the app can receive keyboard input at all.
///
/// Root cause of "arrow keys do nothing": an unbundled executable (`swift run
/// PhotoSelectorApp`, or the raw `.build/…/PhotoSelectorApp` binary) starts with activation
/// policy `.prohibited`. Such a process can never become the active application, so the
/// window server delivers **no key events** to it — every keystroke goes to the previously
/// frontmost app (Terminal). Mouse clicks still reach the window, which is why buttons worked
/// and the keyboard router's unit tests passed while the real app ignored every key.
@MainActor
public enum ForegroundActivation {

    /// Switches to `.regular` when needed. Returns `true` when the policy was changed.
    @discardableResult
    public static func ensureKeyboardCapable(_ app: ActivationPolicyControlling) -> Bool {
        guard app.activationPolicy() != .regular else { return false }
        return app.setActivationPolicy(.regular)
    }

    /// The three conditions under which the OS routes key events to this process's window.
    public static func canReceiveKeyboard(
        policy: NSApplication.ActivationPolicy,
        isActive: Bool,
        hasKeyWindow: Bool
    ) -> Bool {
        policy == .regular && isActive && hasKeyWindow
    }
}
