import Testing
import CoreGraphics
import Foundation
@testable import PhotoSelectorKit

@Suite("MetadataPanelLayout — metadata reserves its own space (REQ-MAC-LAYOUT.05)")
struct MetadataPanelLayoutTests {

    private func overlap(_ a: CGRect, _ b: CGRect) -> Bool {
        let i = a.intersection(b)
        return !i.isNull && i.width > 0.001 && i.height > 0.001
    }

    @Test("Width-bound landscape photo (canvas barely wider than the photo): bottom strip, never overlapping, tiling the canvas")
    func widthBoundUsesBottomStrip() {
        let canvas = CGSize(width: 1000, height: 700)
        let layout = MetadataPanelLayout(canvas: canvas, aspectRatio: 1.5, isPanelVisible: true)
        #expect(layout.placement == .bottomStrip)
        #expect(!overlap(layout.image, layout.panel))
        #expect(layout.image.union(layout.panel) == CGRect(origin: .zero, size: canvas))
        #expect(layout.panel.height == MetadataPanelLayout.stripHeight)
        #expect(layout.panel.width == canvas.width)
    }

    @Test("Height-bound landscape photo in a wide canvas: the side column costs the photo nothing")
    func heightBoundLandscapeUsesSideColumn() {
        let canvas = CGSize(width: 1280, height: 620)
        let layout = MetadataPanelLayout(canvas: canvas, aspectRatio: 1.5, isPanelVisible: true)
        #expect(layout.placement == .sideColumn)
        let with = ZoomGeometry.fittedSize(content: CGSize(width: 3, height: 2), in: layout.image.size)
        let without = ZoomGeometry.fittedSize(content: CGSize(width: 3, height: 2), in: canvas)
        #expect(with == without)
        #expect(!overlap(layout.image, layout.panel))
    }

    @Test("Portrait photo in a wide canvas: side column (width is not the binding constraint)")
    func portraitUsesSideColumn() {
        let canvas = CGSize(width: 1280, height: 640)
        let layout = MetadataPanelLayout(canvas: canvas, aspectRatio: 2.0 / 3.0, isPanelVisible: true)
        #expect(layout.placement == .sideColumn)
        #expect(!overlap(layout.image, layout.panel))
        #expect(layout.image.union(layout.panel) == CGRect(origin: .zero, size: canvas))
        // Portrait fitted size is unchanged by the side column: no photo area was given up.
        let withPanel = ZoomGeometry.fittedSize(content: CGSize(width: 2, height: 3), in: layout.image.size)
        let without = ZoomGeometry.fittedSize(content: CGSize(width: 2, height: 3), in: canvas)
        #expect(withPanel.height == without.height)
    }

    @Test("Chosen arrangement never fits a smaller photo than the rejected one")
    func choiceMaximisesFittedArea() {
        for canvas in [CGSize(width: 960, height: 460), CGSize(width: 1280, height: 640), CGSize(width: 700, height: 900), CGSize(width: 2000, height: 1000)] {
            for ratio in stride(from: CGFloat(0.5), through: 2.4, by: 0.1) {
                let layout = MetadataPanelLayout(canvas: canvas, aspectRatio: ratio, isPanelVisible: true)
                let strip = MetadataPanelLayout.fittedArea(aspectRatio: ratio, in: CGSize(width: canvas.width, height: canvas.height - min(MetadataPanelLayout.stripHeight, canvas.height * 0.25)))
                let side = MetadataPanelLayout.fittedArea(aspectRatio: ratio, in: CGSize(width: canvas.width - min(MetadataPanelLayout.sideWidth, canvas.width * 0.33), height: canvas.height))
                let chosen = MetadataPanelLayout.fittedArea(aspectRatio: ratio, in: layout.image.size)
                #expect(chosen * MetadataPanelLayout.sideColumnAdvantage >= max(strip, side) - 0.5, "canvas \(canvas) ratio \(ratio)")
                #expect(!overlap(layout.image, layout.panel))
            }
        }
    }

    @Test("Hidden panel gives the photo the whole canvas")
    func hiddenPanel() {
        let canvas = CGSize(width: 1000, height: 600)
        let layout = MetadataPanelLayout(canvas: canvas, aspectRatio: 1.5, isPanelVisible: false)
        #expect(layout.placement == .hidden)
        #expect(layout.image == CGRect(origin: .zero, size: canvas))
        #expect(layout.panel == .zero)
    }

    @Test("Unknown aspect ratio falls back to 3:2; tiny canvases keep a non-zero panel and a non-negative image")
    func floorsAndFallbacks() {
        let unknown = MetadataPanelLayout(canvas: CGSize(width: 1000, height: 600), aspectRatio: nil, isPanelVisible: true)
        let landscape = MetadataPanelLayout(canvas: CGSize(width: 1000, height: 600), aspectRatio: 1.5, isPanelVisible: true)
        #expect(unknown == landscape)

        let tiny = MetadataPanelLayout(canvas: CGSize(width: 120, height: 80), aspectRatio: 1.5, isPanelVisible: true)
        #expect(tiny.panel.width > 0 && tiny.panel.height > 0)
        #expect(tiny.image.width >= 0 && tiny.image.height >= 0)
        #expect(!overlap(tiny.image, tiny.panel))

        let zero = MetadataPanelLayout(canvas: .zero, aspectRatio: 1.5, isPanelVisible: true)
        #expect(zero.placement == .hidden)
        let bogus = MetadataPanelLayout(canvas: CGSize(width: 800, height: 500), aspectRatio: -3, isPanelVisible: true)
        #expect(bogus.placement == .bottomStrip)
    }

    @Test("Across window sizes and photo shapes the panel never costs the photo more than a bottom strip would")
    func photoRetention() {
        for canvas in [CGSize(width: 960, height: 460), CGSize(width: 1280, height: 620), CGSize(width: 1920, height: 900), CGSize(width: 800, height: 800)] {
            for ratio in [CGFloat(2.0 / 3.0), 1.0, 1.5, 1.78] {
                let layout = MetadataPanelLayout(canvas: canvas, aspectRatio: ratio, isPanelVisible: true)
                let kept = MetadataPanelLayout.fittedArea(aspectRatio: ratio, in: layout.image.size)
                let full = MetadataPanelLayout.fittedArea(aspectRatio: ratio, in: canvas)
                let stripOnly = pow((canvas.height - MetadataPanelLayout.stripHeight) / canvas.height, 2)
                #expect(kept / full >= stripOnly / MetadataPanelLayout.sideColumnAdvantage - 0.001, "canvas \(canvas) ratio \(ratio)")
            }
        }
    }
}
