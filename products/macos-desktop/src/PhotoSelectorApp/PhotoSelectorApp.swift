import SwiftUI
import AppKit
import PhotoSelectorKit

@main
struct PhotoSelectorApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: appDelegate.viewModel)
                .preferredColorScheme(.dark)
        }
        .defaultSize(width: 1280, height: 840)
        .commands {
            AppCommands(viewModel: appDelegate.viewModel)
        }

        Settings {
            SettingsView()
                .preferredColorScheme(.dark)
        }
    }
}

/// Owns the workspace view model and makes the process keyboard-capable at launch.
///
/// `ForegroundActivation.ensureKeyboardCapable` runs in `applicationWillFinishLaunching`:
/// without it an unbundled binary keeps activation policy `.prohibited`, the window server
/// never makes it the active app, and arrow keys go to Terminal instead (REQ-MAC-KEY.01).
@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate, ObservableObject {
    let viewModel = CullingWorkspaceViewModel()
    let launchOptions = LaunchOptions(arguments: ProcessInfo.processInfo.arguments)
    private var selfTest: UISelfTestRunner?

    func applicationWillFinishLaunching(_ notification: Notification) {
        ForegroundActivation.ensureKeyboardCapable(NSApp)
        if launchOptions.selfTestFolder != nil {
            UITestFrameRegistry.shared.isEnabled = true
        }
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.activate(ignoringOtherApps: true)
        DispatchQueue.main.async {
            Self.makeWorkspaceWindowKey()
        }

        if let folder = launchOptions.selfTestFolder {
            let runner = UISelfTestRunner(
                viewModel: viewModel,
                folder: folder,
                reportURL: launchOptions.reportURL,
                expectedCount: launchOptions.expectedCount,
                externalKeyWait: launchOptions.externalKeyWait
            )
            selfTest = runner
            Task { @MainActor in
                let passed = await runner.run()
                NSApp.terminate(passed ? 0 : 1)
                exit(passed ? 0 : 1)
            }
        } else if let folder = launchOptions.openFolder {
            viewModel.loadFolder(at: folder)
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }

    /// Brings the first ordinary (non-panel) window to the front and makes it key.
    static func makeWorkspaceWindowKey() {
        guard let window = NSApp.windows.first(where: { $0.canBecomeKey && !$0.isSheet && $0.isVisible })
                ?? NSApp.windows.first(where: { $0.canBecomeKey && !$0.isSheet }) else { return }
        window.makeKeyAndOrderFront(nil)
    }
}

/// Command-line options understood by the app (all optional):
/// `--open <folder>` loads a folder at launch;
/// `--ui-self-test <folder> [--report <json>] [--expect-count N] [--external-key-wait S]`
/// runs the in-app UI self-test and exits 0 (pass) or 1 (fail).
struct LaunchOptions {
    var openFolder: URL?
    var selfTestFolder: URL?
    var reportURL: URL?
    var expectedCount: Int?
    var externalKeyWait: TimeInterval = 0

    init(arguments: [String]) {
        var iterator = arguments.dropFirst().makeIterator()
        while let argument = iterator.next() {
            switch argument {
            case "--open":
                openFolder = iterator.next().map { URL(fileURLWithPath: $0, isDirectory: true) }
            case "--ui-self-test":
                selfTestFolder = iterator.next().map { URL(fileURLWithPath: $0, isDirectory: true) }
            case "--report":
                reportURL = iterator.next().map { URL(fileURLWithPath: $0) }
            case "--expect-count":
                expectedCount = iterator.next().flatMap { Int($0) }
            case "--external-key-wait":
                externalKeyWait = iterator.next().flatMap { TimeInterval($0) } ?? 0
            default:
                continue
            }
        }
    }
}
