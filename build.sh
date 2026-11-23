#!/usr/bin/env bash
# 简账 / MiniLedger —— 纯 framework Java，无 Gradle、无 androidx
# 依赖本机 DayFlowBuild 里的 JDK17 + build-tools 35 + platform 35
set -euo pipefail

B=/mnt/c/Users/llyyt/AppData/Local/DayFlowBuild
export JAVA_HOME="$B/jdk"
export PATH="$JAVA_HOME/bin:$B/sdk/build-tools/35.0.0:$B/sdk/platform-tools:$PATH"

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$PROJ/app/src/main"
OUT="$PROJ/build"
ANDROID_JAR="$B/sdk/platforms/android-35/android.jar"
BT="$B/sdk/build-tools/35.0.0"
APK="$PROJ/dist/miniledger.apk"
KS="$PROJ/keystore.jks"

: "${MINILEDGER_STORE_PASS:?请先设置环境变量 MINILEDGER_STORE_PASS（签名密码，不写入仓库）}"
: "${MINILEDGER_KEY_PASS:?请先设置环境变量 MINILEDGER_KEY_PASS（签名密码，不写入仓库）}"

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/classes" "$OUT/dex" "$OUT/gen" "$PROJ/dist"

echo "[1/7] compile resources"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/compiled/res.zip"

echo "[2/7] link resources"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 35 \
  --version-code 1 --version-name 1.0 \
  "$OUT/compiled/res.zip"

echo "[3/7] compile java"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -nowarn \
  -bootclasspath "$ANDROID_JAR:$BT/core-lambda-stubs.jar" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/7] dex"
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --release --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

echo "[5/7] merge dex into apk"
python3 - "$OUT/base.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY

echo "[6/7] align"
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7/7] sign"
if [ ! -f "$KS" ]; then
  echo "  generating keystore"
  keytool -genkeypair -keystore "$KS" -alias miniledger -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$MINILEDGER_STORE_PASS" -keypass "$MINILEDGER_KEY_PASS" \
    -dname "CN=MiniLedger, OU=App, O=MiniLedger, L=Beijing, ST=Beijing, C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign \
  --ks "$KS" --ks-pass pass:"$MINILEDGER_STORE_PASS" --key-pass pass:"$MINILEDGER_KEY_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$APK" "$OUT/aligned.apk"

"$BT/apksigner" verify --verbose "$APK" | head -5
echo "---"
ls -lh "$APK"
echo "OK -> $APK"
