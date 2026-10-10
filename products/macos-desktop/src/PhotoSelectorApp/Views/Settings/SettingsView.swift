import SwiftUI
import PhotoSelectorKit

/// Native 3-tab macOS Settings view accessible via Preferences / Settings (⌘,).
public struct SettingsView: View {
    private enum Tab: Hashable {
        case general
        case scoring
        case performance
    }

    public init() {}

    public var body: some View {
        TabView {
            GeneralSettingsTab()
                .tabItem {
                    Label("General", systemImage: "gearshape")
                }
                .tag(Tab.general)

            ScoringSettingsTab()
                .tabItem {
                    Label("Scoring & Aesthetics", systemImage: "wand.and.stars")
                }
                .tag(Tab.scoring)

            CachePerformanceSettingsTab()
                .tabItem {
                    Label("Cache & Performance", systemImage: "speedometer")
                }
                .tag(Tab.performance)
        }
        .frame(width: 540)
    }
}

// MARK: - Tab 1: General
struct GeneralSettingsTab: View {
    @AppStorage(AppSettingsKeys.cullingDestinationFolderName)
    private var cullingDestinationFolderName: String = AppSettingsDefaults.cullingDestinationFolderName

    @AppStorage(AppSettingsKeys.autoAdvance)
    private var autoAdvance: Bool = AppSettingsDefaults.autoAdvance

    var body: some View {
        Form {
            Section("Culling Destination") {
                LabeledContent("Destination Folder") {
                    TextField("Folder Name", text: $cullingDestinationFolderName)
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 180)
                }
                Text("Selected and copied photos will be placed into <Root>/\(sanitizedFolderName)/.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Navigation & Culling") {
                Toggle("Auto-advance after culling action", isOn: $autoAdvance)
                Text("Automatically steps to the next photo when pressing Move (M), Copy (C), or Trash (Delete).")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .padding(16)
    }

    private var sanitizedFolderName: String {
        let trimmed = cullingDestinationFolderName.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? AppSettingsDefaults.cullingDestinationFolderName : trimmed
    }
}

// MARK: - Tab 2: Scoring & Aesthetics
struct ScoringSettingsTab: View {
    @AppStorage(AppSettingsKeys.sharpnessBlurryThreshold)
    private var sharpnessBlurryThreshold: Double = AppSettingsDefaults.sharpnessBlurryThreshold

    @AppStorage(AppSettingsKeys.sharpnessAcceptableThreshold)
    private var sharpnessAcceptableThreshold: Double = AppSettingsDefaults.sharpnessAcceptableThreshold

    @AppStorage(AppSettingsKeys.aestheticThreshold)
    private var aestheticThreshold: Double = AppSettingsDefaults.aestheticThreshold

    var body: some View {
        Form {
            Section("Sharpness & Focus Thresholds (Accelerate)") {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Blurry / Soft Threshold:")
                        Spacer()
                        Text(String(format: "%.1f", sharpnessBlurryThreshold))
                            .monospacedDigit()
                            .foregroundStyle(.orange)
                    }
                    Slider(value: $sharpnessBlurryThreshold, in: 10.0...50.0, step: 1.0)

                    HStack {
                        Text("Acceptable / Sharp Threshold:")
                        Spacer()
                        Text(String(format: "%.1f", sharpnessAcceptableThreshold))
                            .monospacedDigit()
                            .foregroundStyle(.green)
                    }
                    Slider(value: $sharpnessAcceptableThreshold, in: 50.0...95.0, step: 1.0)
                }
                Text("Photos with variance below \(Int(sharpnessBlurryThreshold)) are flagged blurry; above \(Int(sharpnessAcceptableThreshold)) are categorized pin-sharp.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Apple Vision Aesthetics") {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Minimum Aesthetic Quality Threshold:")
                        Spacer()
                        Text(String(format: "%.1f / 10.0", aestheticThreshold))
                            .monospacedDigit()
                            .foregroundStyle(.yellow)
                    }
                    Slider(value: $aestheticThreshold, in: 0.0...9.0, step: 0.5)
                }
                Text("Evaluates visual composition and appeal using Apple Vision VNCalculateImageAestheticsScoresRequest.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .padding(16)
    }
}

// MARK: - Tab 3: Cache & Performance
@MainActor
final class CacheTabState: ObservableObject {
    @Published var purgeFeedback: String?
}

struct CachePerformanceSettingsTab: View {
    @AppStorage(AppSettingsKeys.thumbnailCacheLimitMB)
    private var thumbnailCacheLimitMB: Int = AppSettingsDefaults.thumbnailCacheLimitMB

    @AppStorage(AppSettingsKeys.preloadWindowBefore)
    private var preloadWindowBefore: Int = AppSettingsDefaults.preloadWindowBefore

    @AppStorage(AppSettingsKeys.preloadWindowAfter)
    private var preloadWindowAfter: Int = AppSettingsDefaults.preloadWindowAfter

    @StateObject private var state = CacheTabState()

    var body: some View {
        Form {
            Section("Decoded Thumbnail Memory Cache") {
                Picker("Cache Memory Ceiling", selection: $thumbnailCacheLimitMB) {
                    Text("256 MB").tag(256)
                    Text("512 MB (Default)").tag(512)
                    Text("1 GB").tag(1024)
                    Text("2 GB").tag(2048)
                    Text("4 GB").tag(4096)
                }
                .onChange(of: thumbnailCacheLimitMB) { _, newLimit in
                    Task {
                        await ThumbnailCache.shared.setMemoryLimitMB(newLimit)
                    }
                }

                HStack {
                    Button("Purge In-Memory Cache") {
                        Task {
                            await ThumbnailCache.shared.removeAll()
                            state.purgeFeedback = "Cache cleared"
                            try? await Task.sleep(nanoseconds: 1_500_000_000)
                            state.purgeFeedback = nil
                        }
                    }

                    if let feedback = state.purgeFeedback {
                        Text(feedback)
                            .font(.caption)
                            .foregroundStyle(.green)
                    }
                }
            }

            Section("Sliding Window Preloading") {
                Stepper("Preload before current: \(preloadWindowBefore) photos", value: $preloadWindowBefore, in: 1...5)
                Stepper("Preload after current: \(preloadWindowAfter) photos", value: $preloadWindowAfter, in: 2...10)
                Text("Pre-decodes textures in memory ahead of keyboard scrub navigation.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .padding(16)
    }
}
