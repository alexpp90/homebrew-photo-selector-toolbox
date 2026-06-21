cask "photo-selector-toolbox" do
  version "0.1.0"
  sha256 "d3b07384d113edec49eaa6238ad5ff00b719029710f27b3858022a106f37803a"

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
