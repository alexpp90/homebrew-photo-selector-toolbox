import SwiftUI
import PhotoSelectorKit

/// Records when each photograph first reached the screen, per tier. Read by the UI
/// self-test to measure time-to-first-photo; costs one dictionary write per displayed photo.
@MainActor
final class DisplayTelemetry {
    static let shared = DisplayTelemetry()

    private(set) var firstDisplayed: [UUID: [ThumbnailTier: Date]] = [:]

    func markDisplayed(photoID: UUID, tier: ThumbnailTier) {
        if firstDisplayed[photoID]?[tier] == nil {
            firstDisplayed[photoID, default: [:]][tier] = Date()
        }
    }

    func firstDisplay(of photoID: UUID, tier: ThumbnailTier) -> Date? {
        firstDisplayed[photoID]?[tier]
    }

    func reset() {
        firstDisplayed.removeAll()
    }
}

/// Per-view image state for a progressively loaded photograph.
@MainActor
final class ProgressivePhotoState: ObservableObject {
    @Published var image: CGImage?
    @Published var tier: ThumbnailTier?
    /// Photo the `image` belongs to — prevents showing the previous photo under a new HUD.
    @Published var photoID: UUID?
    @Published var panOffset: CGSize = .zero

    /// Streams placeholder → preview for `photo`, then resolves missing EXIF and scores
    /// off the main actor.
    func load(
        photo: PhotoItem,
        onUpdateExif: ((UUID, ExifData) -> Void)?,
        onUpdateScores: ((UUID, QualityScores) -> Void)?
    ) async {
        if photoID != photo.id {
            image = nil
            tier = nil
            photoID = photo.id
        }

        var preview: CGImage?
        for await progressive in ThumbnailLoader.shared.progressivePreview(for: photo.url) {
            guard !Task.isCancelled, photoID == photo.id else { return }
            image = progressive.image
            tier = progressive.tier
            DisplayTelemetry.shared.markDisplayed(photoID: photo.id, tier: progressive.tier)
            if progressive.tier == .preview { preview = progressive.image }
        }
        guard !Task.isCancelled else { return }

        if photo.exif == nil || photo.exif?.isFallback == true {
            let url = photo.url
            let exif = await Task.detached(priority: .utility) {
                CullingWorkspaceViewModel.readMetadataWithCompanionFallback(for: url)
            }.value
            if let exif, !exif.isFallback, !Task.isCancelled {
                onUpdateExif?(photo.id, exif)
            }
        }

        if photo.scores == nil, let preview, !Task.isCancelled {
            let wrapped = CGImageWrapper(preview)
            let scores = await Task.detached(priority: .utility) {
                await QualityScoringService.score(cgImage: wrapped.image)
            }.value
            if !Task.isCancelled {
                onUpdateScores?(photo.id, scores)
            }
        }
    }
}

/// Per-viewport interactive state (magnification, drag origin, and decoded full-res image).
@MainActor
final class ZoomableViewportState: ObservableObject {
    @Published var liveMagnification: CGFloat = 1
    @Published var dragOrigin: CGSize?
    @Published var originalPixelSize: CGSize?
    @Published var fullResolutionImage: CGImage?
}

/// Image viewport with fit / honest-100 % / pinch zoom and clamped panning.
///
/// Hit-testing rule (the cause of "no buttons work while zoomed"): gestures are attached to
/// the **untransformed** viewport with a rectangular `contentShape`, and the scaled image has
/// hit-testing disabled. `.clipped()` only clips drawing, so gestures on the scaled image
/// used to capture clicks far outside the canvas — over the toolbar and the action bar.
@MainActor
struct ZoomableImageViewport: View {
    let image: CGImage?
    let photoURL: URL
    let zoomState: ZoomState
    @Binding var panOffset: CGSize
    let onDoubleClick: () -> Void
    let onPinchEnded: (Double) -> Void

    @Environment(\.displayScale) private var displayScale
    @StateObject private var viewport = ZoomableViewportState()

    var body: some View {
        GeometryReader { geometry in
            let viewportSize = geometry.size
            let contentSize = viewport.originalPixelSize ?? image.map { CGSize(width: $0.width, height: $0.height) }
            let baseScale = ZoomGeometry.scale(
                for: zoomState,
                originalPixelSize: contentSize,
                viewport: viewportSize,
                backingScale: displayScale
            )
            let scale = max(1, baseScale * viewport.liveMagnification)
            let fitted = contentSize.map { ZoomGeometry.fittedSize(content: $0, in: viewportSize) } ?? viewportSize

            ZStack {
                if let displayed = (zoomState.isZoomed ? viewport.fullResolutionImage : nil) ?? image {
                    Image(decorative: displayed, scale: 1.0)
                        .resizable()
                        .interpolation(.high)
                        .aspectRatio(contentMode: .fit)
                        .frame(width: viewportSize.width, height: viewportSize.height)
                        .scaleEffect(scale)
                        .offset(ZoomGeometry.clampedPan(panOffset, scale: scale, fittedSize: fitted, viewport: viewportSize))
                        .allowsHitTesting(false)
                } else {
                    ProgressView()
                        .scaleEffect(1.1)
                        .tint(.secondary)
                }
            }
            .frame(width: viewportSize.width, height: viewportSize.height)
            .clipped()
            .contentShape(Rectangle())
            .gesture(
                MagnifyGesture()
                    .onChanged { value in
                        viewport.liveMagnification = value.magnification
                    }
                    .onEnded { value in
                        let final = Double(baseScale * value.magnification)
                        viewport.liveMagnification = 1
                        onPinchEnded(final)
                    }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 2)
                    .onChanged { value in
                        guard scale > 1 else { return }
                        let origin = viewport.dragOrigin ?? panOffset
                        viewport.dragOrigin = origin
                        panOffset = ZoomGeometry.clampedPan(
                            CGSize(width: origin.width + value.translation.width,
                                   height: origin.height + value.translation.height),
                            scale: scale,
                            fittedSize: fitted,
                            viewport: viewportSize
                        )
                    }
                    .onEnded { _ in viewport.dragOrigin = nil }
            )
            .onTapGesture(count: 2) { onDoubleClick() }
            .onHover { inside in
                guard zoomState.isZoomed else { return }
                if inside { NSCursor.openHand.push() } else { NSCursor.pop() }
            }
        }
        .onChange(of: zoomState) { _, newState in
            if !newState.isZoomed {
                panOffset = .zero
                viewport.fullResolutionImage = nil // release the large decode immediately
            }
        }
        .onChange(of: photoURL) { _, _ in
            viewport.fullResolutionImage = nil
            viewport.originalPixelSize = nil
        }
        .task(id: FullResolutionRequest(url: photoURL, zoomed: zoomState.isZoomed)) {
            if viewport.originalPixelSize == nil {
                let url = photoURL
                viewport.originalPixelSize = await Task.detached(priority: .utility) {
                    ThumbnailLoader.originalPixelSize(of: url)
                }.value
            }
            guard zoomState.isZoomed, viewport.fullResolutionImage == nil else { return }
            let decoded = await ThumbnailLoader.shared.loadFullResolution(for: photoURL)
            if !Task.isCancelled, zoomState.isZoomed {
                viewport.fullResolutionImage = decoded
            }
        }
    }
}

private struct FullResolutionRequest: Hashable {
    let url: URL
    let zoomed: Bool
}

/// Floating banner shown whenever any zoom is active: states the zoom and offers a visible
/// way back to Fit (in addition to Esc, Space, double-click and the toolbar button).
struct ZoomExitPill: View {
    let zoomState: ZoomState
    let onExit: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "plus.magnifyingglass")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.white)
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.white)
            Text("Drag to pan")
                .font(.system(size: 11))
                .foregroundStyle(Color(white: 0.85))
            Button(action: onExit) {
                HStack(spacing: 4) {
                    Image(systemName: "arrow.down.right.and.arrow.up.left")
                    Text("Fit (Esc)")
                }
                .font(.system(size: 11, weight: .bold))
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
            }
            .buttonStyle(.borderedProminent)
            .focusable(false)
            .uiTestFrame("zoom_exit_button")
            .help("Return to Fit (Esc or Space)")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 0.5))
        .shadow(color: .black.opacity(0.35), radius: 6, y: 2)
    }

    private var label: String {
        switch zoomState {
        case .fit: return "Fit"
        case .oneToOne: return "100% (1:1)"
        case .magnified(let factor): return String(format: "Zoom %.1f×", factor)
        }
    }
}
