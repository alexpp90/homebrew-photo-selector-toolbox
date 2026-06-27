class PhotoSelectorToolbox < Formula
  desc "Analyze image EXIF metadata, find duplicates, and detect blur"
  homepage "https://github.com/alexpp90/homebrew-photo-selector-toolbox"
  version "0.4.0"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/v#{version}/photo-selector-toolbox-macos-apple-silicon.zip"
      sha256 "1d983c6b26d3ecdf487141db59692533be0614ccfca25a9c88304adff56cae39" # macos_sha256
    end
  end

  on_linux do
    if Hardware::CPU.intel?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/v#{version}/photo-selector-toolbox-linux-x64.zip"
      sha256 "a2c50665b1906f55cb31ea0f6de18b586c5dce0b1c1ef83239f23dcf3c92e8dd" # linux_sha256
    end
  end

  conflicts_with "photo-selector-toolbox@nightly", because: "both install the same binaries"

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
