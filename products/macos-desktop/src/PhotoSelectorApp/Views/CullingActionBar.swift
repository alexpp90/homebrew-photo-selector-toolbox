import SwiftUI
import PhotoSelectorKit

/// Prominent culling action bar offering intuitive, one-click buttons with explicit shortcut badges
/// for moving, copying, and trashing candidate photographs.
public struct CullingActionBar: View {
    @ObservedObject public var viewModel: CullingWorkspaceViewModel

    public init(viewModel: CullingWorkspaceViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 14) {
                // MARK: - Culling Actions

                // 1. Move to Selection Button (Green Primary Action)
                Button(action: {
                    viewModel.cullCurrentPhoto(action: .move)
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.right.doc.fill")
                            .font(.system(size: 14, weight: .bold))

                        VStack(alignment: .leading, spacing: 1) {
                            Text("Move to Selection")
                                .font(.system(size: 13, weight: .bold))
                            Text("Shortcut: M")
                                .font(.system(size: 10, weight: .regular))
                                .opacity(0.85)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 7)
                    .frame(minWidth: 165)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.green)
                .focusable(false)
                .disabled(viewModel.photos.isEmpty)
                .help("Move active photo and companion RAW/XMP files to Selection folder (Press M)")

                // 2. Copy to Selection Button (Blue Action)
                Button(action: {
                    viewModel.cullCurrentPhoto(action: .copy)
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.on.doc.fill")
                            .font(.system(size: 14, weight: .bold))

                        VStack(alignment: .leading, spacing: 1) {
                            Text("Copy to Selection")
                                .font(.system(size: 13, weight: .bold))
                            Text("Shortcut: C")
                                .font(.system(size: 10, weight: .regular))
                                .opacity(0.85)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 7)
                    .frame(minWidth: 165)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.blue)
                .focusable(false)
                .disabled(viewModel.photos.isEmpty)
                .help("Copy active photo and companion files to Selection folder (Press C)")

                // 3. Move to Trash Button (Red Destructive Action)
                Button(action: {
                    viewModel.cullCurrentPhoto(action: .trash)
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "trash.fill")
                            .font(.system(size: 14, weight: .bold))

                        VStack(alignment: .leading, spacing: 1) {
                            Text("Move to Trash")
                                .font(.system(size: 13, weight: .bold))
                            Text("Shortcut: ⌫ / Del")
                                .font(.system(size: 10, weight: .regular))
                                .opacity(0.85)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 7)
                    .frame(minWidth: 155)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.red)
                .focusable(false)
                .disabled(viewModel.photos.isEmpty)
                .help("Move active photo and companion files to macOS Trash (Press Delete)")

                Divider()
                    .frame(height: 28)

                // 4. Undo Button
                Button(action: {
                    viewModel.undoLastAction()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.uturn.backward")
                            .font(.system(size: 12, weight: .semibold))
                        Text("Undo")
                            .font(.system(size: 12, weight: .semibold))
                        Text("⌘Z")
                            .font(.system(size: 10, weight: .medium, design: .monospaced))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                }
                .buttonStyle(.bordered)
                .focusable(false)
                .disabled(!viewModel.canUndo)
                .help("Undo the last move, copy, or trash action (⌘Z)")

                Spacer()

                // MARK: - Culling Progress Counts
                HStack(spacing: 12) {
                    HStack(spacing: 5) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                            .font(.system(size: 12))
                        Text("\(viewModel.selectedCount) Selected")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.white)
                    }

                    HStack(spacing: 5) {
                        Image(systemName: "trash.fill")
                            .foregroundStyle(.red)
                            .font(.system(size: 12))
                        Text("\(viewModel.trashedCount) Trashed")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.white)
                    }

                    let remaining = viewModel.photos.count - viewModel.selectedCount - viewModel.trashedCount
                    Text("\(max(0, remaining)) Remaining")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Color.white.opacity(0.75))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.5))
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(Color.white.opacity(0.15), lineWidth: 0.5)
                )

                // Shortcuts Help Button
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
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 6)

            // MARK: - Bottom Shortcuts Hint Strip
            HStack(spacing: 12) {
                Text("💡 Hotkeys:")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.85))

                HStack(spacing: 8) {
                    shortcutPill(key: "← / →", label: "Navigate")
                    shortcutPill(key: "M", label: "Move to Selection")
                    shortcutPill(key: "C", label: "Copy to Selection")
                    shortcutPill(key: "⌫ Del", label: "Trash")
                    shortcutPill(key: "⌘Z", label: "Undo")
                    shortcutPill(key: "Space", label: "100% Zoom")
                    shortcutPill(key: "1 / 2 / 3", label: "View Modes")
                }

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 6)
        }
        .background(Color(red: 0.08, green: 0.08, blue: 0.09))
        .preferredColorScheme(.dark)
        .overlay(Divider(), alignment: .top)
    }

    private func shortcutPill(key: String, label: String) -> some View {
        HStack(spacing: 4) {
            Text(key)
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundStyle(Color.white)
                .padding(.horizontal, 5)
                .padding(.vertical, 2)
                .background(Color.white.opacity(0.18))
                .clipShape(RoundedRectangle(cornerRadius: 3))

            Text(label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.white.opacity(0.8))
        }
    }
}
