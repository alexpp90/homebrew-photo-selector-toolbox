import SwiftUI
import PhotoSelectorKit

/// Compact bottom filmstrip / scrubber view (<= 12% window height).
/// Supports smooth horizontal scrolling, auto-scroll centering on navigation,
/// status badges, and focus quality dots.
public struct FilmstripView: View {
    public let photos: [PhotoItem]
    public let currentIndex: Int
    public let comparisonIndices: [Int]
    public let onSelectPhoto: (Int) -> Void

    public init(
        photos: [PhotoItem],
        currentIndex: Int,
        comparisonIndices: [Int],
        onSelectPhoto: @escaping (Int) -> Void
    ) {
        self.photos = photos
        self.currentIndex = currentIndex
        self.comparisonIndices = comparisonIndices
        self.onSelectPhoto = onSelectPhoto
    }

    public var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 8) {
                    ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                        FilmstripThumbnailTile(
                            photo: photo,
                            isSelected: index == currentIndex,
                            isInComparison: comparisonIndices.contains(index),
                            onTap: { onSelectPhoto(index) }
                        )
                        .id(photo.id)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
            }
            .background(Color(red: 0.043, green: 0.043, blue: 0.051))
            .overlay(
                Rectangle()
                    .frame(height: 0.5)
                    .foregroundStyle(Color.white.opacity(0.1)),
                alignment: .top
            )
            .onChange(of: currentIndex) { _, newIndex in
                if photos.indices.contains(newIndex) {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        proxy.scrollTo(photos[newIndex].id, anchor: .center)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

@MainActor
final class FilmstripTileState: ObservableObject {
    @Published var thumbnailImage: CGImage?
}

/// An individual thumbnail tile within the filmstrip with proportional true-aspect ratio.
public struct FilmstripThumbnailTile: View {
    public let photo: PhotoItem
    public let isSelected: Bool
    public let isInComparison: Bool
    public let onTap: () -> Void

    @StateObject private var state = FilmstripTileState()

    private var tileWidth: CGFloat {
        if let cgImage = state.thumbnailImage, cgImage.height > 0 {
            let ratio = CGFloat(cgImage.width) / CGFloat(cgImage.height)
            let clampedRatio = max(0.56, min(2.4, ratio))
            return max(36.0, min(144.0, 64.0 * clampedRatio))
        }
        return 64.0
    }

    public var body: some View {
        let width = tileWidth
        Button(action: onTap) {
            ZStack(alignment: .topTrailing) {
                // Background & Thumbnail (Proportional aspect ratio, .fit to preserve framing)
                ZStack {
                    Color(red: 0.08, green: 0.08, blue: 0.09)

                    if let cgImage = state.thumbnailImage {
                        Image(decorative: cgImage, scale: 1.0)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                    } else {
                        ProgressView()
                            .scaleEffect(0.6)
                            .tint(.secondary)
                    }
                }
                .frame(width: width, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 6))

                // Culling Status Icon (Top-Right)
                if photo.status != .candidate {
                    statusIcon(for: photo.status)
                        .padding(3)
                }

                // Sharpness Dot (Bottom-Right)
                if let scores = photo.scores {
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            Circle()
                                .fill(sharpnessColor(for: scores.focusCategory))
                                .frame(width: 6, height: 6)
                                .padding(4)
                        }
                    }
                }

                // Selection Border (Obsidian Viewfinder Cyan #38BDF8 for selected photo)
                RoundedRectangle(cornerRadius: 6)
                    .strokeBorder(
                        isSelected ? Color(red: 0.22, green: 0.74, blue: 0.97) : (isInComparison ? Color.cyan.opacity(0.8) : Color.white.opacity(0.08)),
                        lineWidth: isSelected ? 2.5 : (isInComparison ? 1.5 : 0.5)
                    )
            }
            .frame(width: width, height: 64)
        }
        .buttonStyle(.plain)
        .focusable(false)
        .task(id: photo.id) {
            state.thumbnailImage = await ThumbnailLoader.shared.loadThumbnail(
                for: photo.url,
                maxPixelSize: ThumbnailTier.filmstrip.rawValue,
                priority: .utility
            )
        }
    }

    @ViewBuilder
    private func statusIcon(for status: PhotoStatus) -> some View {
        switch status {
        case .candidate:
            EmptyView()
        case .selected:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 13))
                .foregroundStyle(Color(red: 16/255.0, green: 185/255.0, blue: 129/255.0))
                .background(Circle().fill(Color.black))
        case .copied:
            Image(systemName: "doc.on.doc.fill")
                .font(.system(size: 11))
                .foregroundStyle(Color(red: 59/255.0, green: 130/255.0, blue: 246/255.0))
                .background(Circle().fill(Color.black))
        case .trashed:
            Image(systemName: "trash.fill")
                .font(.system(size: 11))
                .foregroundStyle(Color(red: 239/255.0, green: 68/255.0, blue: 68/255.0))
                .background(Circle().fill(Color.black))
        }
    }

    private func sharpnessColor(for category: SharpnessCategory) -> Color {
        switch category {
        case .sharp: return Color(red: 52/255.0, green: 211/255.0, blue: 153/255.0)      // #34D399
        case .acceptable: return Color(red: 245/255.0, green: 158/255.0, blue: 11/255.0) // #F59E0B
        case .blurry: return Color(red: 244/255.0, green: 63/255.0, blue: 94/255.0)      // #F43F5E
        }
    }
}
