cask "photo-selector-toolbox" do
  version "0.5.0"
  sha256 "7e596e12db088f93133fd438cb6093eec457cf44e454b4113ea05156b6075f4e" # macos_sha256

  url "https://github.com/alexpp90/photo-selector/releases/download/v#{version}/photo-selector-macos-apple-silicon.zip"
  name "PhotoSelector Studio"
  desc "High-performance native macOS photo culling and selection application (Swift 6 & Apple Vision)"
  homepage "https://github.com/alexpp90/photo-selector"

  conflicts_with cask: "photo-selector-toolbox@nightly"
  depends_on macos: ">= :sonoma"

  app "PhotoSelector.app"

  zap trash: "~/Library/Preferences/com.photoselectortoolbox.macos.plist"
end
