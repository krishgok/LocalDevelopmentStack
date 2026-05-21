#!/usr/bin/env bash
set -euo pipefail

REPO="krishgok/localdevstack"
BINARY_NAME="localdevstack"
INSTALL_DIR="${LOCALDEVSTACK_INSTALL_DIR:-/usr/local/bin}"

OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)

case "${OS}" in
  darwin)
    case "${ARCH}" in
      arm64)  PLATFORM="macos-arm64" ;;
      x86_64)
        echo "Intel macOS pre-built binaries are no longer published."
        echo "Build from source via Homebrew: brew install --build-from-source krishgok/localdevstack/localdevstack"
        exit 1
        ;;
      *)      echo "Unsupported architecture: ${ARCH}"; exit 1 ;;
    esac
    ;;
  linux)
    case "${ARCH}" in
      x86_64|amd64) PLATFORM="linux-x64" ;;
      *)            echo "Unsupported architecture: ${ARCH}. Only x86_64 is supported on Linux."; exit 1 ;;
    esac
    ;;
  *)
    echo "Unsupported OS: ${OS}."
    echo "For Windows, use the PowerShell installer:"
    echo "  irm https://raw.githubusercontent.com/krishgok/localdevstack/main/scripts/install.ps1 | iex"
    exit 1
    ;;
esac

VERSION="${LOCALDEVSTACK_VERSION:-}"
if [ -z "${VERSION}" ]; then
  VERSION=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
    | grep '"tag_name"' | sed 's/.*"v\([^"]*\)".*/\1/')
fi

TARBALL="localdevstack-${VERSION}-${PLATFORM}.tar.gz"
URL="https://github.com/${REPO}/releases/download/v${VERSION}/${TARBALL}"
SHA256_URL="${URL}.sha256"

echo "Installing LocalDevelopmentStack v${VERSION} for ${PLATFORM}..."

TMP=$(mktemp -d)
trap 'rm -rf "${TMP}"' EXIT

curl -fsSL "${URL}" -o "${TMP}/${TARBALL}"

EXPECTED=$(curl -fsSL "${SHA256_URL}" | awk '{print $1}')
if command -v sha256sum &>/dev/null; then
  ACTUAL=$(sha256sum "${TMP}/${TARBALL}" | awk '{print $1}')
elif command -v shasum &>/dev/null; then
  ACTUAL=$(shasum -a 256 "${TMP}/${TARBALL}" | awk '{print $1}')
else
  echo "WARNING: Cannot verify checksum (sha256sum/shasum not found). Proceeding anyway."
  ACTUAL="${EXPECTED}"
fi

if [ "${ACTUAL}" != "${EXPECTED}" ]; then
  echo "Checksum mismatch!"
  echo "  Expected : ${EXPECTED}"
  echo "  Got      : ${ACTUAL}"
  exit 1
fi

tar xzf "${TMP}/${TARBALL}" -C "${TMP}"
BINARY="${TMP}/localdevstack-${PLATFORM}"
chmod +x "${BINARY}"

if [ -w "${INSTALL_DIR}" ]; then
  mv "${BINARY}" "${INSTALL_DIR}/${BINARY_NAME}"
else
  echo "Writing to ${INSTALL_DIR} requires sudo..."
  sudo mv "${BINARY}" "${INSTALL_DIR}/${BINARY_NAME}"
fi

echo ""
echo "LocalDevelopmentStack v${VERSION} installed to ${INSTALL_DIR}/${BINARY_NAME}"
echo ""
echo "Run: ${BINARY_NAME} --help"
