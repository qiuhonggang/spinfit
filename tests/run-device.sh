#!/bin/bash
# Usage: tests/run-device.sh [adb serial]. Confirm installation on the phone if prompted.
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
TEST_BT="$TEST_SDK/build-tools/36.1.0"
TEST_JAR="$TEST_SDK/platforms/android-35/android.jar"
TEST_OUT="$PWD/app/bin/device-tests"
ADB_ARGS=()
if [ "$#" -gt 0 ]; then ADB_ARGS=(-s "$1"); fi
mkdir -p "$TEST_OUT/obj" "$TEST_OUT/stage"
cat > "$TEST_OUT/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.spinfit.bike.tests"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/><application android:label="SpinFit checks"/><instrumentation android:name="com.spinfit.bike.tests.DeviceChecks" android:targetPackage="com.spinfit.bike"/><instrumentation android:name="com.spinfit.bike.tests.DeviceAudit" android:targetPackage="com.spinfit.bike"/><instrumentation android:name="com.spinfit.bike.tests.UiChecks" android:targetPackage="com.spinfit.bike"/></manifest>
XML
"$TEST_BT/aapt2" link -I "$TEST_JAR" --manifest "$TEST_OUT/AndroidManifest.xml" -o "$TEST_OUT/base.apk"
javac -encoding UTF-8 -source 8 -target 8 -cp "$TEST_JAR:$PWD/app/obj" -d "$TEST_OUT/obj" tests/DeviceChecks.java tests/DeviceAudit.java tests/UiChecks.java
find "$TEST_OUT/obj" -name '*.class' > "$TEST_OUT/classes.txt"
"$TEST_BT/d8" --lib "$TEST_JAR" --classpath app/bin/SpinFit.apk --min-api 26 --output "$TEST_OUT/stage" @"$TEST_OUT/classes.txt"
(cd "$TEST_OUT/stage" && zip -q "$TEST_OUT/base.apk" classes.dex)
"$TEST_BT/apksigner" sign --ks app/debug.keystore --ks-pass pass:android --out "$TEST_OUT/checks.apk" "$TEST_OUT/base.apk"
adb "${ADB_ARGS[@]}" install --no-incremental -r "$TEST_OUT/checks.apk"
adb "${ADB_ARGS[@]}" shell am instrument -w com.spinfit.bike.tests/com.spinfit.bike.tests.DeviceChecks
