cask "photo-selector-toolbox" do
  version "0.4.0"
  sha256 "1d983c6b26d3ecdf487141db59692533be0614ccfca25a9c88304adff56cae39" # macos_sha256

  url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/v#{version}/photo-selector-toolbox-macos-apple-silicon.zip"
  name "Photo Selector Toolbox"
  desc "Analyze image EXIF metadata, find duplicates, and detect blur"
  homepage "https://github.com/alexpp90/homebrew-photo-selector-toolbox"

  conflicts_with cask: "photo-selector-toolbox@nightly"
  depends_on :macos

  app "Photo Selector Toolbox.app"
  binary "photo-selector-toolbox/photo-selector-toolbox"

  zap trash: "~/Library/Preferences/com.alexpp90.photo-selector-toolbox.plist"
end
