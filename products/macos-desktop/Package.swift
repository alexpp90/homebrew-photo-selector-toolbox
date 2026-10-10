// swift-tools-version: 6.0
import Foundation
import PackageDescription
var testingPluginFlags: [String] = []

let candidateDirs = [
    ProcessInfo.processInfo.environment["DEVELOPER_DIR"],
    "/Applications/Xcode.app/Contents/Developer",
    "/Applications/Xcode_26.6.app/Contents/Developer",
    "/Applications/Xcode_16.2.app/Contents/Developer",
    "/Applications/Xcode_16.1.app/Contents/Developer",
    "/Applications/Xcode_16.0.app/Contents/Developer",
    "/Applications/Xcode_16.app/Contents/Developer",
    "/Library/Developer/CommandLineTools",
].compactMap { $0 }

for dir in candidateDirs {
    let toolchainTesting = (dir as NSString).appendingPathComponent("Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/host/plugins/testing")
    if FileManager.default.fileExists(atPath: toolchainTesting) {
        testingPluginFlags = ["-plugin-path", toolchainTesting]
        break
    }
    let cltTesting = (dir as NSString).appendingPathComponent("usr/lib/swift/host/plugins/testing")
    if FileManager.default.fileExists(atPath: cltTesting) {
        testingPluginFlags = ["-plugin-path", cltTesting]
        break
    }
}

let package = Package(
    name: "PhotoSelector",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "PhotoSelectorKit",
            targets: ["PhotoSelectorKit"]
        ),
        .executable(
            name: "PhotoSelectorApp",
            targets: ["PhotoSelectorApp"]
        )
    ],
    dependencies: [],
    targets: [
        // Headless Domain & Infrastructure Framework
        .target(
            name: "PhotoSelectorKit",
            dependencies: [],
            path: "src/PhotoSelectorKit",
            linkerSettings: [
                .unsafeFlags(["-Xlinker", "-w"])
            ]
        ),
        // Native SwiftUI macOS Executable
        .executableTarget(
            name: "PhotoSelectorApp",
            dependencies: [
                "PhotoSelectorKit"
            ],
            path: "src/PhotoSelectorApp",
            linkerSettings: [
                .unsafeFlags(["-Xlinker", "-w"])
            ]
        ),
        // Automated Unit Tests
        .testTarget(
            name: "PhotoSelectorKitTests",
            dependencies: [
                "PhotoSelectorKit"
            ],
            path: "tests/unit",
            swiftSettings: testingPluginFlags.isEmpty ? [] : [
                .unsafeFlags(testingPluginFlags)
            ],
            linkerSettings: [
                .unsafeFlags(["-Xlinker", "-w"])
            ]
        )
    ]
)
