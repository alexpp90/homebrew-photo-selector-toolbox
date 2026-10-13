cask "photo-selector-toolbox-legacy" do
  version "0.5.0"
  sha256 "1d983c6b26d3ecdf487141db59692533be0614ccfca25a9c88304adff56cae39" # macos_sha256

  url "https://github.com/alexpp90/photo-selector/releases/download/v#{version}/photo-selector-toolbox-macos-apple-silicon.zip"
  name "Photo Selector Toolbox (Legacy)"
  desc "Legacy Python/Tkinter photo selection and EXIF metadata suite"
  homepage "https://github.com/alexpp90/photo-selector"

  conflicts_with cask: "photo-selector-toolbox-legacy@nightly"
  depends_on :macos

  app "Photo Selector Toolbox.app"
  binary "photo-selector-toolbox/photo-selector-toolbox", target: "photo-selector-toolbox-legacy"

  zap trash: "~/Library/Preferences/com.alexpp90.photo-selector-toolbox.plist"
end
