// Generates landscape (and a few portrait) JPEG fixtures for the macOS UI smoke test.
//
// Usage: swift make_fixtures.swift <output-dir> <count>
// Writes <count> photos (90 % landscape 3:2, 10 % portrait 2:3) at camera-like resolution,
// spread over the root and one nested folder, plus decoy photos inside an excluded
// `Selection/` folder that the scanner must skip. Prints the expected photo count.
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let arguments = CommandLine.arguments
guard arguments.count >= 3, let count = Int(arguments[2]), count > 0 else {
    FileHandle.standardError.write(Data("usage: make_fixtures.swift <output-dir> <count>\n".utf8))
    exit(2)
}
let root = URL(fileURLWithPath: arguments[1], isDirectory: true)
let nested = root.appendingPathComponent("DCIM/100CANON", isDirectory: true)
let excluded = root.appendingPathComponent("Selection", isDirectory: true)
for dir in [root, nested, excluded] {
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
}

func writeJPEG(to url: URL, width: Int, height: Int, hue: CGFloat) {
    let space = CGColorSpaceCreateDeviceRGB()
    guard let ctx = CGContext(
        data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
        space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    ) else { return }
    // Gradient background plus a checker pattern so the focus metric has edges to measure.
    let colors = [
        CGColor(red: hue, green: 0.3, blue: 1 - hue, alpha: 1),
        CGColor(red: 1 - hue, green: 0.8, blue: hue, alpha: 1),
    ] as CFArray
    if let gradient = CGGradient(colorsSpace: space, colors: colors, locations: [0, 1]) {
        ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: width, y: height), options: [])
    }
    ctx.setFillColor(CGColor(gray: 0, alpha: 0.35))
    let cell = max(16, width / 40)
    for y in stride(from: 0, to: height, by: cell * 2) {
        for x in stride(from: 0, to: width, by: cell * 2) {
            ctx.fill(CGRect(x: x, y: y, width: cell, height: cell))
        }
    }
    guard let image = ctx.makeImage(),
          let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil)
    else { return }
    CGImageDestinationAddImage(dest, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
    CGImageDestinationFinalize(dest)
}

for index in 0..<count {
    let portrait = index % 10 == 9
    let (w, h) = portrait ? (2000, 3000) : (3000, 2000)
    let folder = index < count / 2 ? root : nested
    let name = String(format: "IMG_%04d.JPG", index + 1)
    writeJPEG(to: folder.appendingPathComponent(name), width: w, height: h, hue: CGFloat(index % 17) / 16)
}
for index in 0..<3 {
    writeJPEG(to: excluded.appendingPathComponent(String(format: "SEL_%04d.JPG", index)), width: 600, height: 400, hue: 0.5)
}
print(count)
