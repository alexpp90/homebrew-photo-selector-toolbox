import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("Settings Persistence & Configuration Unit Tests")
struct SettingsPersistenceTests {

    private func createIsolatedDefaults() -> (UserDefaults, String) {
        let suiteName = "test.settings.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        return (defaults, suiteName)
    }

    @Test("REQ-MAC-SETTINGS.01: Default Values: unconfigured store returns documented application defaults")
    func testDefaultValues() {
        let (defaults, suiteName) = createIsolatedDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }

        #expect(defaults.cullingDestinationFolderName == AppSettingsDefaults.cullingDestinationFolderName)
        #expect(defaults.cullingDestinationFolderName == "Selection")
        #expect(defaults.autoAdvance == AppSettingsDefaults.autoAdvance)
        #expect(defaults.autoAdvance == true)
        #expect(defaults.sharpnessBlurryThreshold == AppSettingsDefaults.sharpnessBlurryThreshold)
        #expect(defaults.sharpnessBlurryThreshold == 35.0)
        #expect(defaults.sharpnessAcceptableThreshold == AppSettingsDefaults.sharpnessAcceptableThreshold)
        #expect(defaults.sharpnessAcceptableThreshold == 70.0)
        #expect(defaults.aestheticThreshold == AppSettingsDefaults.aestheticThreshold)
        #expect(defaults.aestheticThreshold == 0.0)
        #expect(defaults.thumbnailCacheLimitMB == AppSettingsDefaults.thumbnailCacheLimitMB)
        #expect(defaults.thumbnailCacheLimitMB == 512)
        #expect(defaults.preloadWindowBefore == AppSettingsDefaults.preloadWindowBefore)
        #expect(defaults.preloadWindowBefore == 2)
        #expect(defaults.preloadWindowAfter == AppSettingsDefaults.preloadWindowAfter)
        #expect(defaults.preloadWindowAfter == 4)
    }

    @Test("Persistence & Mutation: updated settings persist and read back correctly across all keys")
    func testSettingsPersistenceAndMutation() {
        let (defaults, suiteName) = createIsolatedDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }

        defaults.cullingDestinationFolderName = "BestPhotos"
        defaults.autoAdvance = false
        defaults.sharpnessBlurryThreshold = 42.0
        defaults.sharpnessAcceptableThreshold = 78.5
        defaults.aestheticThreshold = 5.5
        defaults.thumbnailCacheLimitMB = 2048
        defaults.preloadWindowBefore = 3
        defaults.preloadWindowAfter = 6

        #expect(defaults.cullingDestinationFolderName == "BestPhotos")
        #expect(defaults.autoAdvance == false)
        #expect(defaults.sharpnessBlurryThreshold == 42.0)
        #expect(defaults.sharpnessAcceptableThreshold == 78.5)
        #expect(defaults.aestheticThreshold == 5.5)
        #expect(defaults.thumbnailCacheLimitMB == 2048)
        #expect(defaults.preloadWindowBefore == 3)
        #expect(defaults.preloadWindowAfter == 6)
    }

    @Test("Reset to Defaults: resets all keys to documented defaults")
    func testResetToDefaults() {
        let (defaults, suiteName) = createIsolatedDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }

        // Mutate
        defaults.cullingDestinationFolderName = "Export"
        defaults.autoAdvance = false
        defaults.sharpnessBlurryThreshold = 20.0
        defaults.sharpnessAcceptableThreshold = 85.0
        defaults.aestheticThreshold = 8.0
        defaults.thumbnailCacheLimitMB = 1024
        defaults.preloadWindowBefore = 1
        defaults.preloadWindowAfter = 8

        // Reset
        defaults.resetAppSettingsToDefaults()

        #expect(defaults.cullingDestinationFolderName == "Selection")
        #expect(defaults.autoAdvance == true)
        #expect(defaults.sharpnessBlurryThreshold == 35.0)
        #expect(defaults.sharpnessAcceptableThreshold == 70.0)
        #expect(defaults.aestheticThreshold == 0.0)
        #expect(defaults.thumbnailCacheLimitMB == 512)
        #expect(defaults.preloadWindowBefore == 2)
        #expect(defaults.preloadWindowAfter == 4)
    }

    @Test("Input Sanitization: empty or whitespace folder names fallback safely to Selection")
    func testFolderNameSanitization() {
        let (defaults, suiteName) = createIsolatedDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }

        defaults.cullingDestinationFolderName = "   "
        #expect(defaults.cullingDestinationFolderName == "Selection")

        defaults.cullingDestinationFolderName = ""
        #expect(defaults.cullingDestinationFolderName == "Selection")

        defaults.cullingDestinationFolderName = "  Keepers  "
        #expect(defaults.cullingDestinationFolderName == "Keepers")
    }

    @Test("AppSettings.reset static method resets userDefaults cleanly")
    func testAppSettingsResetStaticMethod() {
        let (defaults, suiteName) = createIsolatedDefaults()
        defer { defaults.removePersistentDomain(forName: suiteName) }

        defaults.cullingDestinationFolderName = "CustomPicks"
        defaults.thumbnailCacheLimitMB = 4096

        AppSettings.reset(userDefaults: defaults)

        #expect(defaults.cullingDestinationFolderName == "Selection")
        #expect(defaults.thumbnailCacheLimitMB == 512)
    }
}
