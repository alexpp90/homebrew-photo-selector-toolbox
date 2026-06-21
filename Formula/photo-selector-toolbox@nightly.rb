class PhotoSelectorToolboxATNightly < Formula
  desc "Analyze image EXIF metadata, find duplicates, and detect blur (Nightly)"
  homepage "https://github.com/alexpp90/homebrew-photo-selector-toolbox"
  version "latest"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-macos-apple-silicon.zip"
      sha256 "7056aa1271646bd306bedd728f1ee4c0dc520e81633ea31cddc323c9b2aab5d2" # macos_sha256
    end
  end

  on_linux do
    if Hardware::CPU.intel?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-linux-x64.zip"
      sha256 "3b844aaa6c462899a9683ae8d6b13e0215d5a4d3c09f1c5c6787e753e2bfb34a" # linux_sha256
    end
  end

  conflicts_with "photo-selector-toolbox", because: "both install the same binaries"

  def install
    if OS.mac?
      bin.install "photo-selector-toolbox/photo-selector-toolbox"
    elsif OS.linux?
      bin.install "photo-selector-toolbox"
      bin.install "Photo Selector Toolbox" => "photo-selector-toolbox-gui"
    end
  end

  test do
    system "#{bin}/photo-selector-toolbox", "--help"
  end
end
