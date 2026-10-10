import SwiftUI

/// Window-space frames of identified views, recorded only while the UI self-test runs.
///
/// The self-test uses these frames to (a) assert the Focus 3-Up geometry the user actually
/// sees and (b) post real mouse clicks onto toolbar buttons while zoomed. In normal use
/// `isEnabled` is false and the modifier adds no geometry observation at all.
@MainActor
final class UITestFrameRegistry {
    static let shared = UITestFrameRegistry()

    var isEnabled = false
    private(set) var frames: [String: CGRect] = [:]

    func record(_ identifier: String, frame: CGRect) {
        guard isEnabled else { return }
        frames[identifier] = frame
    }

    func remove(_ identifier: String) {
        frames.removeValue(forKey: identifier)
    }

    func frame(_ identifier: String) -> CGRect? {
        frames[identifier]
    }
}

private struct UITestFrameModifier: ViewModifier {
    let identifier: String

    func body(content: Content) -> some View {
        if UITestFrameRegistry.shared.isEnabled {
            content
                .accessibilityIdentifier(identifier)
                .background(
                    GeometryReader { proxy in
                        Color.clear
                            .onAppear {
                                UITestFrameRegistry.shared.record(identifier, frame: proxy.frame(in: .global))
                            }
                            .onChange(of: proxy.frame(in: .global)) { _, newFrame in
                                UITestFrameRegistry.shared.record(identifier, frame: newFrame)
                            }
                            .onDisappear {
                                UITestFrameRegistry.shared.remove(identifier)
                            }
                    }
                )
        } else {
            content.accessibilityIdentifier(identifier)
        }
    }
}

extension View {
    /// Tags the view with an accessibility identifier and, during the UI self-test, records
    /// its window-space frame in `UITestFrameRegistry`.
    func uiTestFrame(_ identifier: String) -> some View {
        modifier(UITestFrameModifier(identifier: identifier))
    }
}
