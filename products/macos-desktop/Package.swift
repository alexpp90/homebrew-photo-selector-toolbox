// swift-tools-version: 6.0
import PackageDescription

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
            swiftSettings: [
                .unsafeFlags([
                    "-plugin-path",
                    "/Library/Developer/CommandLineTools/usr/lib/swift/host/plugins/testing"
                ])
            ],
            linkerSettings: [
                .unsafeFlags(["-Xlinker", "-w"])
            ]
        )
    ]
)
