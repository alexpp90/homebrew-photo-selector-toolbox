import Testing
import Foundation
@testable import PhotoSelectorKit

/// Companion grouping against the table every product shares
/// (`testdata/formats/companions.json`, finding D-6): Move, Trash and Undo must carry a shot's
/// sidecars and its `-Edit` / `_Edit` / `-Enhanced-NR` / `_Enhanced-NR` derivatives with it.
struct CompanionCase: Decodable, Sendable, CustomTestStringConvertible {
    let name: String
    let files: [String]
    let bundles: [[String]]
    var testDescription: String { name }
}

private struct CompanionTable: Decodable {
    let cases: [CompanionCase]

    static let shared: [CompanionCase] = {
        let url = FormatManifest.directory.appendingPathComponent("companions.json")
        let data = try! Data(contentsOf: url)
        return try! JSONDecoder().decode(CompanionTable.self, from: data).cases
    }()
}

@Suite("Companion grouping — shared table")
struct CompanionGroupingTableTests {

    @Test("findCompanionFiles follows the shared companion table", arguments: CompanionTable.shared)
    func groupsAsTheTableSays(_ tableCase: CompanionCase) throws {
        let base = FileManager.default.temporaryDirectory.standardizedFileURL.resolvingSymlinksInPath()
        let dir = base.appendingPathComponent("CompanionTable_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        for name in tableCase.files {
            try Data("x".utf8).write(to: dir.appendingPathComponent(name))
        }

        let manager = FileCullingManager()
        for bundle in tableCase.bundles {
            let shot = dir.appendingPathComponent(bundle[0])
            let found = Set(manager.findCompanionFiles(for: shot).map { $0.lastPathComponent.lowercased() })
            #expect(found == Set(bundle.map { $0.lowercased() }), "bundle of \(bundle[0])")
        }
    }

    @Test("discovery shows one primary per bundle of the shared companion table", arguments: CompanionTable.shared)
    func discoveryGroupsAsTheTableSays(_ tableCase: CompanionCase) {
        let dir = URL(fileURLWithPath: "/virtual/shoot", isDirectory: true)
        let urls = tableCase.files.map { dir.appendingPathComponent($0) }

        let primaries = DirectoryScanner.groupPrimaryPhotos(in: urls)

        #expect(
            Set(primaries.map { $0.lastPathComponent.lowercased() }) ==
                Set(tableCase.bundles.map { $0[0].lowercased() })
        )
    }
}
