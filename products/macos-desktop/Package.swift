// swift-tools-version: 6.0
import Foundation
import PackageDescription

var testingPluginFlags: [String] = []
let candidatePluginPaths = [
    "/Library/Developer/CommandLineTools/usr/lib/swift/host/plugins/testing",
    "/Applications/Xcode_16.2.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/host/plugins/testing",
    "/Applications/Xcode_16.1.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/host/plugins/testing",
    "/Applications/Xcode_16.0.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/host/plugins/testing",
    "/Applications/Xcode_16.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/host/plugins/testing",
    "/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/host/plugins/testing",
]

for path in candidatePluginPaths {
    if FileManager.default.fileExists(atPath: path) {
        testingPluginFlags.append(contentsOf: ["-plugin-path", path])
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
