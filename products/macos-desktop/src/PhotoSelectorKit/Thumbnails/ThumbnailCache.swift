import Foundation
import CoreGraphics

/// Thread-safe Sendable wrapper around a CoreGraphics CGImage.
public final class CGImageWrapper: @unchecked Sendable {
    public let image: CGImage

    public init(_ image: CGImage) {
        self.image = image
    }
}

/// Cache key uniquely identifying an image URL and target pixel downsampling tier.
public struct ThumbnailCacheKey: Hashable, Sendable {
    public let url: URL
    public let maxPixelSize: Int

    public init(url: URL, maxPixelSize: Int) {
        self.url = url.standardizedFileURL.resolvingSymlinksInPath()
        self.maxPixelSize = maxPixelSize
    }

    public var keyString: String {
        "\(url.path)::\(maxPixelSize)"
    }
}

/// Actor-isolated, memory-bounded cache for decoded thumbnails and preview images.
/// Uses CoreGraphics uncompressed byte cost accounting (bytesPerRow * height)
/// with a default ceiling of 512 MB and automatic OS memory pressure handling.
public actor ThumbnailCache {

    public static let shared = ThumbnailCache()

    public static let minMemoryLimitMB: Int = 256
    public static let maxMemoryLimitMB: Int = 4096

    public static let defaultMemoryLimit: Int = 512 * 1024 * 1024 // 512 MB
    public static let defaultCountLimit: Int = 300

    private let cache = NSCache<NSString, CGImageWrapper>()

    public init(
        memoryLimitBytes: Int? = nil,
        countLimit: Int = ThumbnailCache.defaultCountLimit,
        userDefaults: UserDefaults = .standard
    ) {
        let effectiveBytes: Int
        if let memoryLimitBytes = memoryLimitBytes {
            effectiveBytes = memoryLimitBytes
        } else {
            let configuredMB = userDefaults.thumbnailCacheLimitMB
            let clampedMB = max(Self.minMemoryLimitMB, min(configuredMB, Self.maxMemoryLimitMB))
            effectiveBytes = clampedMB * 1024 * 1024
        }
        self.cache.totalCostLimit = effectiveBytes
        self.cache.countLimit = countLimit
    }

    /// Retrieves a cached image if available.
    public func image(for key: ThumbnailCacheKey) -> CGImage? {
        return cache.object(forKey: key.keyString as NSString)?.image
    }

    /// Stores an image in the cache with exact byte cost accounting.
    public func setImage(_ image: CGImage, for key: ThumbnailCacheKey) {
        let cost = image.bytesPerRow * image.height
        cache.setObject(CGImageWrapper(image), forKey: key.keyString as NSString, cost: cost)
    }

    /// Removes a specific cached representation.
    public func removeImage(for key: ThumbnailCacheKey) {
        cache.removeObject(forKey: key.keyString as NSString)
    }

    /// Removes all cached tiers for a given photo URL.
    public func removeImages(for url: URL) {
        let standardSizes = [384, 1024, 1536, 2048]
        for size in standardSizes {
            let key = ThumbnailCacheKey(url: url, maxPixelSize: size)
            cache.removeObject(forKey: key.keyString as NSString)
        }
    }

    /// Flushes the entire cache.
    public func removeAll() {
        cache.removeAllObjects()
    }

    /// Dynamically sets the total cache memory limit in megabytes.
    public func setMemoryLimitMB(_ megabytes: Int) {
        cache.totalCostLimit = megabytes * 1024 * 1024
    }

    /// Dynamically sets the total cache memory limit in bytes.
    public func setMemoryLimitBytes(_ bytes: Int) {
        cache.totalCostLimit = bytes
    }
}
