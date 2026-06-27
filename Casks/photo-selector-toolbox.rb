cask "photo-selector-toolbox" do
  version "0.4.0"
  sha256 "7f12023c7d0b228d15c094098bba82c563307938b2bd131a83feaf2983522382" # macos_sha256

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
