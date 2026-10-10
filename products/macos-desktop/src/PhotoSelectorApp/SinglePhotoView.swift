import SwiftUI
import PhotoSelectorKit

/// Maximized 1-Up preview canvas occupying >85% to 100% of window area.
/// Progressive display (embedded-thumbnail placeholder → 2048 preview), non-occluding
/// floating HUD pill, and honest 1:1 zoom through `ZoomableImageViewport`.
public struct SinglePhotoView: View {
    public let photo: PhotoItem
    public let isHUDVisible: Bool
    public let zoomState: ZoomState
    public let onToggleZoom: () -> Void
    public var onPinchEnded: ((Double) -> Void)?
    public var onUpdateExif: ((UUID, ExifData) -> Void)?
    public var onUpdateScores: ((UUID, QualityScores) -> Void)?

    @StateObject private var state = ProgressivePhotoState()

    public init(
        photo: PhotoItem,
        isHUDVisible: Bool,
        zoomState: ZoomState,
        onToggleZoom: @escaping () -> Void,
        onPinchEnded: ((Double) -> Void)? = nil,
        onUpdateExif: ((UUID, ExifData) -> Void)? = nil,
        onUpdateScores: ((UUID, QualityScores) -> Void)? = nil
    ) {
        self.photo = photo
        self.isHUDVisible = isHUDVisible
        self.zoomState = zoomState
        self.onToggleZoom = onToggleZoom
        self.onPinchEnded = onPinchEnded
        self.onUpdateExif = onUpdateExif
        self.onUpdateScores = onUpdateScores
    }

    public var body: some View {
        ZStack(alignment: .topLeading) {
            Color(red: 0.055, green: 0.055, blue: 0.063)
                .ignoresSafeArea()

            ZoomableImageViewport(
                image: state.photoID == photo.id ? state.image : nil,
                photoURL: photo.url,
                zoomState: zoomState,
                panOffset: $state.panOffset,
                onDoubleClick: onToggleZoom,
                onPinchEnded: { onPinchEnded?($0) }
            )

            // Floating Non-Occluding Info HUD Pill (hidden while zoomed: nothing may cover
            // the detail being inspected).
            if isHUDVisible && !zoomState.isZoomed {
                FloatingInfoPillView(photo: photo)
                    .padding(.top, 20)
                    .padding(.leading, 16)
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        .clipped()
        .accessibilityIdentifier("single_photo_view")
        .onChange(of: photo.id) { _, _ in state.panOffset = .zero }
        .task(id: photo.id) {
            await state.load(photo: photo, onUpdateExif: onUpdateExif, onUpdateScores: onUpdateScores)
        }
    }
}

/// Floating glassmorphic HUD pill displaying optical EXIF, sharpness, and aesthetic scores.
public struct FloatingInfoPillView: View {
    public let photo: PhotoItem

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header Row: Filename and Format Badge
            HStack(spacing: 8) {
                Text(photo.filename)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primary)

                let ext = photo.url.pathExtension.uppercased()
                Text(ext)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Color.accentColor)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.accentColor.opacity(0.18))
                    .clipShape(Capsule())

                // Status Badge
                StatusBadge(status: photo.status)

                Spacer()
            }

            // Exposure Row: Camera, Shutter, Aperture, ISO, Focal Length, Lens
            if let exif = photo.exif, !exif.isFallback {
                VStack(alignment: .leading, spacing: 3) {
                    if let camera = exif.cameraModel, !camera.isEmpty {
                        HStack(spacing: 5) {
                            Image(systemName: "camera.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(Color.accentColor)
                            Text(camera)
                                .font(.system(size: 11, weight: .bold))
                            if !exif.lens.isEmpty && exif.lens != "Unknown" {
                                Text("· \(exif.lens)")
                                    .font(.system(size: 11, weight: .regular))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }

                    HStack(spacing: 8) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                        Text(exif.formattedSummary)
                            .font(.system(size: 11, weight: .semibold).monospacedDigit())
                            .foregroundStyle(.primary)
                    }
                }
            } else if let exif = photo.exif, exif.isFallback {
                HStack(spacing: 5) {
                    Image(systemName: "camera")
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                    Text("No EXIF metadata")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
            } else {
                HStack(spacing: 5) {
                    Image(systemName: "camera")
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                    Text("Reading EXIF…")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
            }

            // Quality Row: Sharpness and Aesthetics
            if let scores = photo.scores {
                HStack(spacing: 12) {
                    HStack(spacing: 4) {
                        Image(systemName: focusIcon(for: scores.focusCategory))
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(focusColor(for: scores.focusCategory))

                        Text("Focus: \(String(format: "%.1f", scores.sharpness))")
                            .font(.system(size: 11, weight: .bold).monospacedDigit())
                            .foregroundStyle(.primary)

                        Text("[\(scores.focusCategory.rawValue.capitalized)]")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(focusColor(for: scores.focusCategory))
                    }

                    if let aesthetic = scores.aesthetic {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(Color.yellow)

                            Text(String(format: "%.1f", aesthetic))
                                .font(.system(size: 11, weight: .semibold).monospacedDigit())
                                .foregroundStyle(.primary)
                        }
                    }

                    if scores.isUtility {
                        HStack(spacing: 4) {
                            Image(systemName: "doc.text.image")
                                .font(.system(size: 10))
                                .foregroundStyle(Color.cyan)
                            Text("Utility")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(Color.cyan)
                        }
                    }
                }
            } else {
                HStack(spacing: 4) {
                    ProgressView()
                        .scaleEffect(0.5)
                        .tint(.secondary)
                    Text("Analyzing image quality…")
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.3), radius: 8, x: 0, y: 3)
    }

    private func focusColor(for category: SharpnessCategory) -> Color {
        switch category {
        case .sharp: return Color.green
        case .acceptable: return Color.orange
        case .blurry: return Color.red
        }
    }

    private func focusIcon(for category: SharpnessCategory) -> String {
        switch category {
        case .sharp: return "checkmark.circle.fill"
        case .acceptable: return "eye.fill"
        case .blurry: return "exclamationmark.triangle.fill"
        }
    }
}
