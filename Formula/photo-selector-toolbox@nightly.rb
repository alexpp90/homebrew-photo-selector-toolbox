class PhotoSelectorToolboxATNightly < Formula
  desc "Analyze image EXIF metadata, find duplicates, and detect blur (Nightly)"
  homepage "https://github.com/alexpp90/homebrew-photo-selector-toolbox"
  version "latest"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-macos-apple-silicon.zip"
      sha256 "5dde97100318cc0feb0bf1e01902027ae35d97edaadc3201e062a663709b0e06" # macos_sha256
    end
  end

  on_linux do
    if Hardware::CPU.intel?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/nightly/photo-selector-toolbox-linux-x64.zip"
      sha256 "bc62b6cf8e4c516419f57fc0af5429b09832293823c893759d250acdfb5b6721" # linux_sha256
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
