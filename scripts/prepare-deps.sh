#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/app/libs" "$ROOT/app/src/main/res/font" "$ROOT/app/src/main/assets/licenses"

# Pin the core build so future upstream changes do not silently change our APK.
XRAY_LIB_TAG="v26.7.5"
AAR="$ROOT/app/libs/libv2ray.aar"
AAR_URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${XRAY_LIB_TAG}/libv2ray.aar"

echo "Downloading AndroidLibXrayLite ${XRAY_LIB_TAG}..."
curl -fL --retry 3 "$AAR_URL" -o "$AAR"

echo "Slimming Xray AAR for arm64 phones..."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
unzip -q "$AAR" -d "$TMP_DIR"

# Keep only arm64-v8a. This removes emulator/x86 and old 32-bit native binaries.
rm -rf "$TMP_DIR/jni/armeabi-v7a" "$TMP_DIR/jni/x86" "$TMP_DIR/jni/x86_64"

# The current app routing does not use geoip/geosite rules. Removing these large
# databases saves about 28 MB without affecting the current config generator.
rm -f "$TMP_DIR/assets/geoip.dat" \
      "$TMP_DIR/assets/geosite.dat" \
      "$TMP_DIR/assets/geoip-only-cn-private.dat"

rm -f "$AAR"
(
  cd "$TMP_DIR"
  zip -qr "$AAR" .
)

echo "Downloading Vazirmatn fonts for build..."
BASE="https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/ttf"
curl -fL --retry 3 "$BASE/Vazirmatn-Regular.ttf" -o "$ROOT/app/src/main/res/font/vazirmatn_regular.ttf"
curl -fL --retry 3 "$BASE/Vazirmatn-Medium.ttf" -o "$ROOT/app/src/main/res/font/vazirmatn_medium.ttf"
curl -fL --retry 3 "$BASE/Vazirmatn-Bold.ttf" -o "$ROOT/app/src/main/res/font/vazirmatn_bold.ttf"

echo "Downloading third-party license texts..."
curl -fL --retry 3 https://raw.githubusercontent.com/2dust/AndroidLibXrayLite/main/LICENSE -o "$ROOT/app/src/main/assets/licenses/AndroidLibXrayLite-LGPL-3.0.txt"
curl -fL --retry 3 https://raw.githubusercontent.com/XTLS/Xray-core/main/LICENSE -o "$ROOT/app/src/main/assets/licenses/Xray-core-MPL-2.0.txt"

echo "Dependencies prepared."
