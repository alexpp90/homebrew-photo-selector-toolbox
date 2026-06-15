cask "photo-selector-toolbox@nightly" do
  version :latest
  sha256 "2311b3f6e45ae68f0462ed2212c28ecdea585c6d9542fd2bfcc39aa058830e10"

  url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-macos-apple-silicon.zip"
  name "Photo Selector Toolbox (Nightly)"
  desc "Analyze image EXIF metadata, find duplicates, and detect blur (Nightly)"
  homepage "https://github.com/alexpp90/homebrew-photo-selector-toolbox"

  conflicts_with cask: "photo-selector-toolbox"
  depends_on :macos

  app "Photo Selector Toolbox.app"
  binary "photo-selector-toolbox/photo-selector-toolbox"

  zap trash: "~/Library/Preferences/com.alexpp90.photo-selector-toolbox.plist"
end
