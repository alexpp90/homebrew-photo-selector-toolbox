import SwiftUI
import PhotoSelectorKit

/// Floating glassmorphic culling action dock with Obsidian Studio palette,
/// tactile embedded hotkey badges, and real-time triage counters.
public struct CullingActionBar: View {
    @ObservedObject public var viewModel: CullingWorkspaceViewModel

    public init(viewModel: CullingWorkspaceViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        HStack(spacing: 10) {
            // MARK: - 1. Move to Selection (M)
            ObsidianDockButton(
                title: "Move to Selection",
                icon: "arrow.right.doc.fill",
                shortcut: "M",
                fillColor: Color(red: 8/255.0, green: 47/255.0, blue: 30/255.0),    // #082F1E
                accentColor: Color(red: 16/255.0, green: 185/255.0, blue: 129/255.0), // #10B981
                isEnabled: !viewModel.photos.isEmpty,
                action: { viewModel.cullCurrentPhoto(action: .move) }
            )
            .accessibilityIdentifier("dock_move")
            .help("Move active photo and companion RAW/XMP files to Selection folder (Press M)")

            // MARK: - 2. Copy to Selection (C)
            ObsidianDockButton(
                title: "Copy to Selection",
                icon: "doc.on.doc.fill",
                shortcut: "C",
                fillColor: Color(red: 17/255.0, green: 34/255.0, blue: 64/255.0),   // #112240
                accentColor: Color(red: 59/255.0, green: 130/255.0, blue: 246/255.0), // #3B82F6
                isEnabled: !viewModel.photos.isEmpty,
                action: { viewModel.cullCurrentPhoto(action: .copy) }
            )
            .accessibilityIdentifier("dock_copy")
            .help("Copy active photo and companion files to Selection folder (Press C)")

            // MARK: - 3. Move to Trash (⌫)
            ObsidianDockButton(
                title: "Move to Trash",
                icon: "trash.fill",
                shortcut: "⌫",
                fillColor: Color(red: 59/255.0, green: 13/255.0, blue: 17/255.0),   // #3B0D11
                accentColor: Color(red: 239/255.0, green: 68/255.0, blue: 68/255.0),  // #EF4444
                isDestructive: true,
                isEnabled: !viewModel.photos.isEmpty,
                action: { viewModel.cullCurrentPhoto(action: .trash) }
            )
            .accessibilityIdentifier("dock_trash")
            .help("Move active photo and companion files to macOS Trash (Press Delete)")

            Divider()
                .frame(height: 22)
                .background(Color.white.opacity(0.15))

            // MARK: - 4. Undo (⌘Z)
            ObsidianDockButton(
                title: "Undo",
                icon: "arrow.uturn.backward",
                shortcut: "⌘Z",
                fillColor: Color(red: 34/255.0, green: 34/255.0, blue: 40/255.0),    // #222228
                accentColor: Color(red: 228/255.0, green: 228/255.0, blue: 231/255.0), // #E4E4E7
                isEnabled: viewModel.canUndo,
                action: { viewModel.undoLastAction() }
            )
            .accessibilityIdentifier("dock_undo")
            .help("Undo the last move, copy, or trash action (⌘Z)")

            Spacer()

            // MARK: - Live Triage Counters HUD
            HStack(spacing: 8) {
                HStack(spacing: 5) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Color(red: 16/255.0, green: 185/255.0, blue: 129/255.0))
                        .font(.system(size: 11))
                    Text("\(viewModel.selectedCount) Selected")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white)
                }

                Text("·")
                    .foregroundStyle(Color.white.opacity(0.35))

                HStack(spacing: 5) {
                    Image(systemName: "trash.fill")
                        .foregroundStyle(Color(red: 239/255.0, green: 68/255.0, blue: 68/255.0))
                        .font(.system(size: 11))
                    Text("\(viewModel.trashedCount) Trashed")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white)
                }

                Text("·")
                    .foregroundStyle(Color.white.opacity(0.35))

                let remaining = max(0, viewModel.photos.count - viewModel.selectedCount - viewModel.trashedCount)
                Text("\(remaining) Remaining")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.75))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.black.opacity(0.45))
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
            )

            // Guide / Shortcuts Help Button
            Button(action: {
                viewModel.presentShortcutsHelp()
            }) {
                Image(systemName: "questionmark.circle")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.white.opacity(0.75))
            }
            .buttonStyle(.plain)
            .focusable(false)
            .help("Show Keyboard Shortcuts & Guide (?)")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 7)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(red: 58/255.0, green: 58/255.0, blue: 66/255.0).opacity(0.85), lineWidth: 1) // #3A3A42
        )
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .preferredColorScheme(.dark)
    }
}

/// Tactile button style for Obsidian Studio dock buttons with responsive click feedback.
private struct ObsidianDockButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1.0)
            .opacity(configuration.isPressed ? 0.85 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// A tactile Obsidian Studio action button with embedded shortcut badge.
private struct ObsidianDockButton: View {
    let title: String
    let icon: String
    let shortcut: String
    let fillColor: Color
    let accentColor: Color
    var isDestructive: Bool = false
    var isEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(accentColor)

                Text(title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.white)

                Text(shortcut)
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundStyle(accentColor)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(Color.black.opacity(0.4))
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
            .background(fillColor)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(accentColor.opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(ObsidianDockButtonStyle())
        .focusable(false)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1.0 : 0.45)
    }
}
