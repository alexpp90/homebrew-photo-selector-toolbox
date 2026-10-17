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
    /// Which modifier instance recorded each identifier's frame. SwiftUI runs the replacement
    /// view's `onAppear` before the old view's `onDisappear` when a slot switches content
    /// (photo pane <-> boundary pane), so removal must only apply to the owner that recorded it.
    private var owners: [String: UUID] = [:]

    func record(_ identifier: String, frame: CGRect, owner: UUID) {
        guard isEnabled else { return }
        frames[identifier] = frame
        owners[identifier] = owner
    }

    func remove(_ identifier: String, owner: UUID) {
        guard owners[identifier] == owner else { return }
        frames.removeValue(forKey: identifier)
        owners.removeValue(forKey: identifier)
    }

    func frame(_ identifier: String) -> CGRect? {
        frames[identifier]
    }
}

private final class FrameOwner: ObservableObject {
    let id = UUID()
}

private struct UITestFrameModifier: ViewModifier {
    let identifier: String
    @StateObject private var owner = FrameOwner()

    func body(content: Content) -> some View {
        if UITestFrameRegistry.shared.isEnabled {
            content
                .accessibilityIdentifier(identifier)
                .background(
                    GeometryReader { proxy in
                        Color.clear
                            .onAppear {
                                UITestFrameRegistry.shared.record(identifier, frame: proxy.frame(in: .global), owner: owner.id)
                            }
                            .onChange(of: proxy.frame(in: .global)) { _, newFrame in
                                UITestFrameRegistry.shared.record(identifier, frame: newFrame, owner: owner.id)
                            }
                            .onDisappear {
                                UITestFrameRegistry.shared.remove(identifier, owner: owner.id)
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
