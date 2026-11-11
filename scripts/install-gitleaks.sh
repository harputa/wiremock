#!/usr/bin/env bash
#
# Installs Gitleaks for this repository only and enables the secret-scanning pre-commit hook.
# Bash equivalent of scripts/install-gitleaks.ps1, for Linux, macOS and Git Bash on Windows.
#
# - Downloads a pinned Gitleaks release into .tools/gitleaks/ (gitignored); nothing is installed globally.
# - Verifies the download against the release's SHA-256 checksums file.
# - Points this repository's git hooks at .githooks/ (git config --local core.hooksPath).
#
# Usage (from anywhere inside the repo):  bash scripts/install-gitleaks.sh
#
# Requires: git, curl or wget, tar (Linux/macOS) or unzip (Windows), sha256sum or shasum.

set -euo pipefail

VERSION="8.30.1"

die() { echo "install-gitleaks: $*" >&2; exit 1; }

root="$(git rev-parse --show-toplevel)" || die "run this script inside the git repository"
dir="$root/.tools/gitleaks"

case "$(uname -s)" in
    Linux*)               os="linux";   ext="tar.gz"; exe_name="gitleaks" ;;
    Darwin*)              os="darwin";  ext="tar.gz"; exe_name="gitleaks" ;;
    MINGW*|MSYS*|CYGWIN*) os="windows"; ext="zip";    exe_name="gitleaks.exe" ;;
    *) die "unsupported operating system: $(uname -s)" ;;
esac

case "$(uname -m)" in
    x86_64|amd64)  arch="x64" ;;
    aarch64|arm64) arch="arm64" ;;
    armv7*)        arch="armv7" ;;
    armv6*)        arch="armv6" ;;
    i386|i686)     arch="x32" ;;
    *) die "unsupported CPU architecture: $(uname -m)" ;;
esac

exe="$dir/$exe_name"

download() { # <url> <output file>
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 -o "$2" "$1"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$2" "$1"
    else
        die "curl or wget is required"
    fi
}

sha256() { # <file>
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        die "sha256sum or shasum is required"
    fi
}

if [ -x "$exe" ] && "$exe" version 2>/dev/null | grep -qF "$VERSION"; then
    installed=true
else
    installed=false
fi

if [ "$installed" = false ]; then
    asset="gitleaks_${VERSION}_${os}_${arch}.${ext}"
    sums="gitleaks_${VERSION}_checksums.txt"
    base="https://github.com/gitleaks/gitleaks/releases/download/v${VERSION}"

    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT

    echo "Downloading Gitleaks v$VERSION ($os/$arch) ..."
    download "$base/$asset" "$tmp/$asset" || die "could not download $asset (no release for $os/$arch?)"
    download "$base/$sums" "$tmp/$sums" || die "could not download $sums"

    expected="$(awk -v name="$asset" '$2 == name {print tolower($1)}' "$tmp/$sums")"
    actual="$(sha256 "$tmp/$asset" | tr '[:upper:]' '[:lower:]')"
    if [ -z "$expected" ] || [ "$actual" != "$expected" ]; then
        die "checksum verification failed for $asset (expected '$expected', got '$actual')"
    fi

    mkdir -p "$tmp/extract"
    if [ "$ext" = "zip" ]; then
        command -v unzip >/dev/null 2>&1 || die "unzip is required"
        unzip -q -o "$tmp/$asset" -d "$tmp/extract"
    else
        tar -xzf "$tmp/$asset" -C "$tmp/extract"
    fi
    [ -f "$tmp/extract/$exe_name" ] || die "$exe_name not found in $asset"

    mkdir -p "$dir"
    mv -f "$tmp/extract/$exe_name" "$exe"
    chmod +x "$exe"
fi

git -C "$root" config --local core.hooksPath .githooks
echo "Gitleaks $("$exe" version) installed in .tools/gitleaks; pre-commit secret scan enabled for this repository."
