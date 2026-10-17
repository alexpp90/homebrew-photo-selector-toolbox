import Foundation

/// Protocol seam for inspecting whether a directory or file resides on a File Provider,
/// cloud-synced mount (`~/Library/CloudStorage/`, iCloud Drive), or non-local volume.
public protocol CloudResourceInspecting: Sendable {
    /// Returns `true` when `url` lives on a remote volume, iCloud Drive, or File Provider mount.
    func isRemoteOrCloudFolder(_ url: URL) -> Bool

    /// Returns `true` when `url` is an online-only placeholder that has not been downloaded locally.
    func isOnlineOnlyItem(_ url: URL) -> Bool

    /// Returns `true` when `url` is shared with other users in a cloud folder.
    func isSharedItem(_ url: URL) -> Bool

    /// Estimates the total bytes that would need to be downloaded to read all `urls` in full.
    func estimatedDownloadBytes(for urls: [URL]) -> Int64
}

/// Default production implementation backed by `URLResourceValues` and macOS File Provider path heuristics.
public struct SystemCloudFolderInspector: CloudResourceInspecting, Sendable {

    private static let cloudPathMarkers: [String] = [
        "/Library/CloudStorage/",
        "/Library/Mobile Documents/"
    ]

    public init() {}

    public func isRemoteOrCloudFolder(_ url: URL) -> Bool {
        let standardizedPath = url.standardizedFileURL.path
        for marker in Self.cloudPathMarkers {
            if standardizedPath.contains(marker) {
                return true
            }
        }

        let keys: Set<URLResourceKey> = [
            .isUbiquitousItemKey,
            .ubiquitousItemDownloadingStatusKey,
            .volumeIsLocalKey
        ]
        if let values = try? url.resourceValues(forKeys: keys) {
            if values.isUbiquitousItem == true {
                return true
            }
            if values.ubiquitousItemDownloadingStatus != nil {
                return true
            }
            if let isLocal = values.volumeIsLocal, !isLocal {
                return true
            }
        }

        return false
    }

    public func isOnlineOnlyItem(_ url: URL) -> Bool {
        let keys: Set<URLResourceKey> = [
            .ubiquitousItemDownloadingStatusKey,
            .fileSizeKey,
            .totalFileAllocatedSizeKey,
            .fileAllocatedSizeKey
        ]
        guard let values = try? url.resourceValues(forKeys: keys) else {
            return false
        }
        if values.ubiquitousItemDownloadingStatus == .notDownloaded {
            return true
        }
        if isRemoteOrCloudFolder(url),
           let logicalSize = values.fileSize, logicalSize > 0,
           let allocated = values.totalFileAllocatedSize ?? values.fileAllocatedSize,
           allocated == 0 {
            return true
        }
        return false
    }

    public func isSharedItem(_ url: URL) -> Bool {
        let keys: Set<URLResourceKey> = [.ubiquitousItemIsSharedKey]
        guard let values = try? url.resourceValues(forKeys: keys) else {
            return false
        }
        return values.ubiquitousItemIsShared == true
    }

    public func estimatedDownloadBytes(for urls: [URL]) -> Int64 {
        let keys: Set<URLResourceKey> = [
            .ubiquitousItemDownloadingStatusKey,
            .totalFileSizeKey,
            .fileSizeKey
        ]
        var total: Int64 = 0
        for url in urls {
            guard let values = try? url.resourceValues(forKeys: keys) else { continue }
            if values.ubiquitousItemDownloadingStatus == .current {
                continue
            }
            if let size = values.totalFileSize ?? values.fileSize, size > 0 {
                total += Int64(size)
            }
        }
        return total
    }
}
