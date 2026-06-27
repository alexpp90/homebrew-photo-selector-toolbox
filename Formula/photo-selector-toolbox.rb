class PhotoSelectorToolbox < Formula
  desc "Analyze image EXIF metadata, find duplicates, and detect blur"
  homepage "https://github.com/alexpp90/homebrew-photo-selector-toolbox"
  version "0.4.0"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/v#{version}/photo-selector-toolbox-macos-apple-silicon.zip"
      sha256 "7f12023c7d0b228d15c094098bba82c563307938b2bd131a83feaf2983522382" # macos_sha256
    end
  end

  on_linux do
    if Hardware::CPU.intel?
      url "https://github.com/alexpp90/homebrew-photo-selector-toolbox/releases/download/v#{version}/photo-selector-toolbox-linux-x64.zip"
      sha256 "fe71cacd74c55612c5a422ecd9a9800b93e92be8cdca9514a0537b64bae5c716" # linux_sha256
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
