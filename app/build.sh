#!/bin/bash
# SpinFit 动感单车 — 手工 Android 构建脚本（aapt2 + javac + d8 + apksigner）
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.1.0"
AJ="$SDK/platforms/android-35/android.jar"
APP="$ROOT/app"
OUT="$APP/bin"

echo "=== [1/7] 清理 ==="
rm -rf "$APP/gen" "$APP/obj" "$OUT"
mkdir -p "$APP/gen" "$APP/obj" "$OUT/stage"

echo "=== [2/7] 编译资源 ==="
"$BT/aapt2" compile --dir "$APP/res" -o "$APP/gen/res.zip"

echo "=== [3/7] 链接资源 + 生成 R.java ==="
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJ" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$APP/gen" \
    --min-sdk-version 26 --target-sdk-version 34 \
    "$APP/gen/res.zip" --auto-add-overlay

echo "=== [4/7] 编译 Java ==="
find "$APP/src" "$APP/gen" -name "*.java" > "$OUT/sources.txt"
# 注意：不能加 -bootclasspath android.jar，否则 javac 无法解析 LambdaMetafactory（lambda 脱糖失败）
javac -encoding UTF-8 -source 8 -target 8 \
    -cp "$AJ" \
    -d "$APP/obj" @"$OUT/sources.txt"

echo "=== [5/7] dex (d8) ==="
find "$APP/obj" -name "*.class" > "$OUT/classes.txt"
"$BT/d8" --lib "$AJ" --min-api 26 --output "$OUT/stage" @"$OUT/classes.txt"
cd "$OUT/stage" && zip -q "$OUT/unaligned.apk" classes.dex && cd "$ROOT"
# 把 dex 加入 base.apk
cd "$OUT/stage" && zip -q "$OUT/base.apk" classes.dex && cd "$ROOT"
cp "$OUT/base.apk" "$OUT/unaligned.apk"

echo "=== [6/7] 对齐 ==="
"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "=== [7/7] 签名 ==="
KS="$APP/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
      -alias androiddebugkey -dname "CN=SpinFit, OU=Dev, O=SpinFit, L=CN, C=CN" \
      -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out "$OUT/SpinFit.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose "$OUT/SpinFit.apk" | head -8

echo "=== 完成 ==="
ls -lh "$OUT/SpinFit.apk"
"$BT/aapt2" dump badging "$OUT/SpinFit.apk" | head -6
