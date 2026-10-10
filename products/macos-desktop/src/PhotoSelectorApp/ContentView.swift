import SwiftUI
import PhotoSelectorKit

/// Primary culling workspace window content view.
/// Coordinates the top navigation bar, full-canvas preview (>80% window area),
/// prominent tactile culling action bar, collapsible filmstrip, zero-latency keyboard routing,
/// drop target ingestion, and modal sheets.
public struct ContentView: View {
    @ObservedObject public var viewModel: CullingWorkspaceViewModel
    @StateObject private var keyboardRouter = KeyboardShortcutRouter()
    @FocusState private var isWorkspaceFocused: Bool

    public init(viewModel: CullingWorkspaceViewModel = CullingWorkspaceViewModel()) {
        self.viewModel = viewModel
    }

    public var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Neutral Dark Studio Canvas
                Color(red: 0.055, green: 0.055, blue: 0.063)
                    .ignoresSafeArea()

                if viewModel.isLoading && viewModel.photos.isEmpty {
                    FolderLoadingView(
                        folderURL: viewModel.rootDirectoryURL,
                        onCancel: { viewModel.cancelLoading() }
                    )
                } else if viewModel.photos.isEmpty {
                    EmptyDropTargetView(
                        isTargeted: viewModel.isDropTargeted,
                        onOpenFolder: { viewModel.presentFolderPicker() }
                    )
                } else {
                    VStack(spacing: 0) {
                        // MARK: - 1. Top Control & Navigation Bar
                        topNavigationBar

                        // MARK: - 2. Main Preview Canvas
                        ZStack(alignment: .topTrailing) {
                            Group {
                                switch viewModel.comparisonMode {
                                case .single:
                                    if let photo = viewModel.currentPhoto {
                                        SinglePhotoView(
                                            photo: photo,
                                            isHUDVisible: viewModel.isInfoHUDVisible,
                                            zoomState: viewModel.zoomState,
                                            onToggleZoom: { viewModel.toggleZoom() },
                                            onPinchEnded: { viewModel.setPinchMagnification($0) },
                                            onUpdateExif: { id, exif in
                                                viewModel.updatePhotoExif(photoID: id, exif: exif)
                                            },
                                            onUpdateScores: { id, scores in
                                                viewModel.updatePhotoScores(photoID: id, scores: scores)
                                            }
                                        )
                                    }
                                case .sideBySide, .triplet:
                                    ComparisonView(
                                        mode: viewModel.comparisonMode,
                                        previousPhoto: viewModel.previousPhoto,
                                        currentPhoto: viewModel.currentPhoto,
                                        nextPhoto: viewModel.nextPhoto,
                                        photos: viewModel.comparisonSlotPhotos,
                                        activeSlotIndex: viewModel.activeSlotIndex,
                                        isZoomSynced: viewModel.isZoomSynced,
                                        isHUDVisible: viewModel.isInfoHUDVisible,
                                        zoomState: viewModel.zoomState,
                                        onSelectSlot: { idx in viewModel.setActiveSlot(idx) },
                                        onToggleZoom: { viewModel.toggleZoom() },
                                        onPinchEnded: { viewModel.setPinchMagnification($0) },
                                        onUpdateExif: { id, exif in
                                            viewModel.updatePhotoExif(photoID: id, exif: exif)
                                        },
                                        onUpdateScores: { id, scores in
                                            viewModel.updatePhotoScores(photoID: id, scores: scores)
                                        }
                                    )
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)

                            // Floating Top-Right Mini Mode Pill (Auxiliary Controls)
                            if viewModel.comparisonMode != .single {
                                HStack(spacing: 6) {
                                    Button(action: { viewModel.toggleZoomSync() }) {
                                        HStack(spacing: 4) {
                                            Image(systemName: viewModel.isZoomSynced ? "link" : "link.badge.slash")
                                            Text(viewModel.isZoomSynced ? "Pan Linked" : "Pan Free")
                                                .font(.system(size: 11, weight: .medium))
                                        }
                                        .foregroundStyle(viewModel.isZoomSynced ? Color.accentColor : Color.secondary)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                    }
                                    .buttonStyle(.plain)
                                    .focusable(false)
                                    .help(viewModel.isZoomSynced ? "Synchronized Pan Active (Press S)" : "Independent Pan Active (Press S)")
                                }
                                .padding(4)
                                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
                                    )
                                .padding(.top, 12)
                                .padding(.trailing, 16)
                            }

                            // Zoom exit pill: a visible way back to Fit whenever any zoom is active.
                            if viewModel.zoomState.isZoomed {
                                ZoomExitPill(zoomState: viewModel.zoomState) {
                                    viewModel.resetZoomToFit()
                                }
                                .uiTestFrame("zoom_exit_pill")
                                .padding(.top, 12)
                                .frame(maxWidth: .infinity, alignment: .center)
                                .transition(.opacity)
                            }

                            // Transient Action Toast Overlay (Center-Bottom of preview)
                            if let toast = viewModel.currentToast {
                                VStack {
                                    Spacer()
                                    CullingHUDView(toast: toast)
                                        .padding(.bottom, 20)
                                        .transition(.asymmetric(
                                            insertion: .scale(scale: 0.9).combined(with: .opacity),
                                            removal: .opacity
                                        ))
                                }
                                .frame(maxWidth: .infinity)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                        // MARK: - 3. Prominent Culling Action Bar
                        CullingActionBar(viewModel: viewModel)

                        // MARK: - 4. Collapsible Bottom Filmstrip
                        if viewModel.isFilmstripVisible {
                            FilmstripView(
                                photos: viewModel.photos,
                                currentIndex: viewModel.currentIndex,
                                comparisonIndices: viewModel.comparisonSlotIndices,
                                onSelectPhoto: { idx in viewModel.selectPhoto(at: idx) }
                            )
                            .frame(height: min(96, geometry.size.height * 0.12))
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }
                }
            }
            .onDrop(of: [.fileURL], isTargeted: $viewModel.isDropTargeted) { providers in
                handleDrop(providers: providers)
            }
        }
        .frame(minWidth: 960, minHeight: 640)
        .preferredColorScheme(.dark)
        .navigationTitle(viewModel.rootDirectoryURL?.lastPathComponent ?? "Photo Selector")
        .focusable()
        .focusEffectDisabled()
        .focused($isWorkspaceFocused)
        .defaultFocus($isWorkspaceFocused, true)
        .onAppear {
            isWorkspaceFocused = true
            keyboardRouter.attach(viewModel: viewModel)
            UISelfTestHooks.keyboardRouter = keyboardRouter
        }
        .onDisappear {
            keyboardRouter.detach()
        }
        // Fallback SwiftUI Zero-Latency Keyboard Routing
        .onKeyPress(.rightArrow) {
            viewModel.navigateToNext()
            return .handled
        }
        .onKeyPress(.leftArrow) {
            viewModel.navigateToPrevious()
            return .handled
        }
        .onKeyPress(.upArrow) {
            viewModel.navigateToPrevious()
            return .handled
        }
        .onKeyPress(.downArrow) {
            viewModel.navigateToNext()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "mM")) { _ in
            viewModel.cullCurrentPhoto(action: .move)
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "cC")) { _ in
            viewModel.cullCurrentPhoto(action: .copy)
            return .handled
        }
        .onKeyPress(.delete) {
            viewModel.cullCurrentPhoto(action: .trash)
            return .handled
        }
        .onKeyPress(.deleteForward) {
            viewModel.cullCurrentPhoto(action: .trash)
            return .handled
        }
        .onKeyPress(.space) {
            viewModel.toggleZoom()
            return .handled
        }
        .onKeyPress(.escape) {
            viewModel.resetZoomToFit() ? .handled : .ignored
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "1")) { _ in
            viewModel.setComparisonMode(.single)
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "2")) { _ in
            viewModel.setComparisonMode(.sideBySide)
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "3")) { _ in
            viewModel.setComparisonMode(.triplet)
            return .handled
        }
        .onKeyPress(.tab) {
            viewModel.advanceComparisonSlotFocus()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "fF\\")) { _ in
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                viewModel.isFilmstripVisible.toggle()
            }
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "iI")) { _ in
            withAnimation(.easeInOut(duration: 0.18)) {
                viewModel.isInfoHUDVisible.toggle()
            }
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "sS")) { _ in
            viewModel.toggleZoomSync()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "?/")) { _ in
            viewModel.presentShortcutsHelp()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "oO"), phases: .down) { press in
            if press.modifiers.contains(.command) {
                viewModel.presentFolderPicker()
                return .handled
            }
            return .ignored
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "zZ"), phases: .down) { press in
            if press.modifiers.contains(.command) {
                viewModel.undoLastAction()
                return .handled
            }
            return .ignored
        }
        .sheet(isPresented: $viewModel.isShortcutsHelpPresented) {
            ShortcutsHelpSheet()
        }
        .sheet(isPresented: $viewModel.isDuplicateFinderPresented) {
            DuplicateFinderView(
                targetDirectoryURL: viewModel.rootDirectoryURL,
                photos: viewModel.photos,
                onPhotoTrashed: { trashedURL in
                    viewModel.markPhotoAsTrashed(url: trashedURL)
                }
            )
        }
        .sheet(isPresented: $viewModel.isLibraryStatisticsPresented) {
            LibraryStatisticsView(workspaceViewModel: viewModel)
        }
    }

    // MARK: - Top Navigation Bar
    private var topNavigationBar: some View {
        HStack(spacing: 12) {
            // Folder Picker Button
            Button(action: { viewModel.presentFolderPicker() }) {
                HStack(spacing: 6) {
                    Image(systemName: "folder.badge.plus")
                        .font(.system(size: 13, weight: .semibold))
                    Text("Open Folder…")
                        .font(.system(size: 12, weight: .semibold))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
            }
            .buttonStyle(.bordered)
            .focusable(false)
            .help("Open photo folder or SD card (⌘O)")

            // Current Directory / Volume Name
            if let rootURL = viewModel.rootDirectoryURL {
                HStack(spacing: 5) {
                    Image(systemName: "sdcard.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.accentColor)
                    Text(rootURL.lastPathComponent)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.white)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 5))
            }

            // Active Ingestion Scanning Pill
            if viewModel.isLoading {
                HStack(spacing: 6) {
                    ProgressView()
                        .controlSize(.small)
                    Text("Scanning… (\(viewModel.photos.count) photos)")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color.accentColor)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.accentColor.opacity(0.15))
                .clipShape(Capsule())
            }

            // Position Counter Badge
            if !viewModel.photos.isEmpty {
                Text("Photo \(viewModel.currentIndex + 1) of \(viewModel.photos.count)")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color.white.opacity(0.9))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.5))
                    .clipShape(Capsule())
            }

            Spacer()

            // Navigation Buttons (Prev / Next)
            HStack(spacing: 6) {
                Button(action: { viewModel.navigateToPrevious() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Prev")
                        Text("←")
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundStyle(Color.white.opacity(0.6))
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                .focusable(false)
                .disabled(viewModel.photos.isEmpty || viewModel.currentIndex == 0)
                .uiTestFrame("toolbar_previous")
                .help("Navigate to previous photo (Left Arrow)")

                Button(action: { viewModel.navigateToNext() }) {
                    HStack(spacing: 4) {
                        Text("Next")
                        Text("→")
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundStyle(Color.white.opacity(0.6))
                        Image(systemName: "chevron.right")
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                .focusable(false)
                .disabled(viewModel.photos.isEmpty || viewModel.currentIndex >= viewModel.photos.count - 1)
                .uiTestFrame("toolbar_next")
                .help("Navigate to next photo (Right Arrow)")
            }

            Divider()
                .frame(height: 18)

            // Comparison View Mode Switcher
            HStack(spacing: 2) {
                modeSegmentButton(mode: .single, label: "1-Up", icon: "rectangle.fill", shortcut: "1")
                modeSegmentButton(mode: .sideBySide, label: "2-Up", icon: "rectangle.split.2x1.fill", shortcut: "2")
                modeSegmentButton(mode: .triplet, label: "Focus 3-Up", icon: "rectangle.split.1x2.fill", shortcut: "3")
            }
            .padding(2)
            .background(Color.black.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 6))

            Divider()
                .frame(height: 18)

            // View Toggles
            HStack(spacing: 6) {
                Button(action: { viewModel.toggleZoom() }) {
                    HStack(spacing: 4) {
                        Image(systemName: viewModel.zoomState.isZoomed ? "arrow.down.right.and.arrow.up.left" : "viewfinder")
                        Text(viewModel.zoomState.isZoomed ? "Fit" : "100%")
                    }
                    .font(.system(size: 11, weight: .medium))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                .focusable(false)
                .uiTestFrame("toolbar_zoom")
                .help(viewModel.zoomState.isZoomed ? "Return to Fit (Space or Esc)" : "Zoom to 100% — one image pixel per screen pixel (Space)")

                Button(action: {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                        viewModel.isFilmstripVisible.toggle()
                    }
                }) {
                    Image(systemName: viewModel.isFilmstripVisible ? "film.fill" : "film")
                        .font(.system(size: 12))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                .focusable(false)
                .help("Toggle Filmstrip (F)")

                Button(action: {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        viewModel.isInfoHUDVisible.toggle()
                    }
                }) {
                    Image(systemName: viewModel.isInfoHUDVisible ? "info.circle.fill" : "info.circle")
                        .font(.system(size: 12))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.bordered)
                .focusable(false)
                .help("Toggle EXIF & Scores HUD (I)")

                Button(action: { viewModel.presentShortcutsHelp() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "questionmark.circle.fill")
                        Text("Guide")
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.accentColor.opacity(0.85))
                .focusable(false)
                .help("Keyboard Shortcuts & Culling Guide (?)")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 7)
        .background(Color(NSColor.windowBackgroundColor).opacity(0.96))
        .overlay(Divider(), alignment: .bottom)
    }

    private func modeSegmentButton(mode: ComparisonMode, label: String, icon: String, shortcut: String) -> some View {
        Button(action: { viewModel.setComparisonMode(mode) }) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.system(size: 11))
                Text(label)
                    .font(.system(size: 11, weight: viewModel.comparisonMode == mode ? .bold : .medium))
                Text("(\(shortcut))")
                    .font(.system(size: 9, design: .monospaced))
                    .opacity(0.7)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(viewModel.comparisonMode == mode ? Color.accentColor : Color.clear)
            .foregroundStyle(viewModel.comparisonMode == mode ? Color.white : Color.primary)
            .clipShape(RoundedRectangle(cornerRadius: 5))
        }
        .buttonStyle(.plain)
        .focusable(false)
        .uiTestFrame("toolbar_mode_\(shortcut)")
        .help("\(label) View Mode (Shortcut: \(shortcut))")
    }

    private func handleDrop(providers: [NSItemProvider]) -> Bool {
        for provider in providers {
            _ = provider.loadObject(ofClass: URL.self) { url, _ in
                if let url {
                    Task { @MainActor in
                        _ = viewModel.handleDroppedURLs([url])
                    }
                }
            }
        }
        return true
    }
}
