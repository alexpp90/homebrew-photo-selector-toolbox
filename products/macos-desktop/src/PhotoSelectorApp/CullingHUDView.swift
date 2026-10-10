import SwiftUI
import PhotoSelectorKit

/// Transient confirmation toast overlay providing immediate visual feedback for actions.
public struct CullingHUDView: View {
    public let toast: ToastMessage

    public init(toast: ToastMessage) {
        self.toast = toast
    }

    public var body: some View {
        HStack(spacing: 12) {
            Image(systemName: toast.icon)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(iconColor)

            VStack(alignment: .leading, spacing: 2) {
                Text(toast.title)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primary)

                if let subtitle = toast.subtitle {
                    Text(subtitle)
                        .font(.system(size: 11, weight: .regular))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(
            Capsule()
                .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.35), radius: 10, x: 0, y: 5)
    }

    private var iconColor: Color {
        if let action = toast.actionType {
            switch action {
            case .move: return Color.green
            case .copy: return Color.blue
            case .trash: return Color.red
            }
        }
        return Color.accentColor
    }
}
