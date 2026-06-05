cask "photo-selector-toolbox" do
  version :latest
  sha256 :no_check

  url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-macos-apple-silicon.zip"
  name "Photo Selector Toolbox"
  desc "Desktop app to analyze image EXIF metadata, find duplicates, and detect blur"
  homepage "https://github.com/alexpp90/homebrew-photo-selector-toolbox"

  app "photo-selector-gui.app"
  binary "photo-selector-toolbox/photo-selector-toolbox"

  zap trash: [
    "~/Library/Preferences/com.alexpp90.photo-selector-toolbox.plist",
  ]
end
