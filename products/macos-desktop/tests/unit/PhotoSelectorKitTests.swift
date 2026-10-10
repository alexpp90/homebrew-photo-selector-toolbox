import Testing
import Foundation
@testable import PhotoSelectorKit

@Suite("PhotoSelectorKit Initialization Tests")
struct PhotoSelectorKitTests {
    @Test("Verify framework initialization and version contract")
    func testFrameworkInitialization() {
        let kit = PhotoSelectorKit()
        #expect(PhotoSelectorKit.version == "1.0.0")
        #expect(PhotoSelectorKit.identifier == "com.alexpp.PhotoSelectorKit")
        #expect(kit.isReady)
    }

    @Test("Verify PhotoItem model instantiation")
    func testPhotoItemModel() {
        let testURL = URL(fileURLWithPath: "/tmp/sample.jpg")
        let item = PhotoItem(url: testURL)
        #expect(item.filename == "sample.jpg")
        #expect(item.url == testURL)
    }
}
