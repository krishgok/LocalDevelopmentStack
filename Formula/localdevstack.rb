class Localdevstack < Formula
  desc "Scaffold a local development stack with hot-reload for any service and database"
  homepage "https://github.com/krishgok/localdevstack"
  version "1.2.1"
  license :proprietary

  on_macos do
    on_arm do
      url "https://github.com/krishgok/localdevstack/releases/download/v1.2.1/localdevstack-1.2.1-macos-arm64.tar.gz"
      sha256 "6cd6462ffaf23d305f759e474713f64b219b400276a73e5e788081202777c21b" # arm64
    end
    # Intel macOS users build from source: `brew install --build-from-source localdevstack`.
    # Pre-built x64 binaries were dropped when GitHub retired the macos-13 runner.
  end

  on_linux do
    on_intel do
      url "https://github.com/krishgok/localdevstack/releases/download/v1.2.1/localdevstack-1.2.1-linux-x64.tar.gz"
      sha256 "2429edea99e684a31bca0800019b52e17f887b0f8755b6c10a75e26366ef5cd0" # linux-x64
    end
  end

  def install
    on_macos do
      bin.install "localdevstack-macos-arm64" => "localdevstack"
    end
    on_linux do
      bin.install "localdevstack-linux-x64" => "localdevstack"
    end
  end

  test do
    system "#{bin}/localdevstack", "--version"
  end
end
