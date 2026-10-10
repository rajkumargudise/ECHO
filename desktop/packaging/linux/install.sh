#!/usr/bin/env bash
# ECHO installer for Linux. ECHO is a product of JARVEX (jarvex.in).
#
#   ./install.sh                  install for the current user (no sudo): ~/.local
#   ./install.sh --system         install for everyone (run with sudo): /usr/local
#   ./install.sh --prefix DIR     install under DIR
#   ./install.sh --uninstall      remove it again (add --system or --prefix if you used them)
#
# It installs the ECHO-*-linux-portable.tar.gz that sits next to this file, adds ECHO to the applications menu,
# and puts an "echo-player" command on your PATH.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
prefix="${HOME}/.local"
uninstall=0
while [ $# -gt 0 ]; do
  case "$1" in
    --system) prefix="/usr/local" ;;
    --prefix) shift; prefix="${1:?--prefix needs a folder}" ;;
    --uninstall) uninstall=1 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

opt="$prefix/opt/echo"
bin="$prefix/bin/echo-player"
desktop="$prefix/share/applications/echo-player.desktop"
icon="$prefix/share/icons/hicolor/256x256/apps/echo-player.png"

if [ "$uninstall" = 1 ]; then
  rm -rf "$opt"
  rm -f "$bin" "$desktop" "$icon"
  command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$prefix/share/applications" >/dev/null 2>&1 || true
  echo "ECHO has been removed. Your music and ECHO's data (~/.local/share/echo) were not touched."
  exit 0
fi

tarball="$(ls "$here"/ECHO-*-linux-portable.tar.gz 2>/dev/null | head -n 1 || true)"
[ -n "$tarball" ] || { echo "Cannot find the ECHO tar.gz next to this installer ($here)." >&2; exit 1; }

echo "Installing ECHO to $opt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
tar -xzf "$tarball" -C "$tmp"
[ -x "$tmp/ECHO/bin/ECHO" ] || { echo "The archive does not contain ECHO/bin/ECHO." >&2; exit 1; }

rm -rf "$opt"
mkdir -p "$prefix/opt" "$prefix/bin" "$prefix/share/applications" "$(dirname "$icon")"
mv "$tmp/ECHO" "$opt"
ln -sf "$opt/bin/ECHO" "$bin"

# The icon sits in the app's lib folder in the portable build; use it when present.
src_icon="$(ls "$opt"/lib/ECHO.png "$opt"/lib/*.png 2>/dev/null | head -n 1 || true)"
[ -n "$src_icon" ] && cp "$src_icon" "$icon" || true

cat > "$desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=ECHO
Comment=Music player by JARVEX (jarvex.in)
Exec=$opt/bin/ECHO
Icon=$icon
Terminal=false
Categories=AudioVideo;Audio;Player;
StartupWMClass=ECHO
DESKTOP
chmod 644 "$desktop"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$prefix/share/applications" >/dev/null 2>&1 || true

echo "ECHO is installed. Find it in your applications menu, or run: echo-player"
case ":$PATH:" in *":$prefix/bin:"*) ;; *) echo "Note: $prefix/bin is not on your PATH yet; the menu entry works either way." ;; esac
