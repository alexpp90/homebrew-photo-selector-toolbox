cask "photo-selector-toolbox@nightly" do
  version :latest
  sha256 "5dde97100318cc0feb0bf1e01902027ae35d97edaadc3201e062a663709b0e06" # macos_sha256

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
