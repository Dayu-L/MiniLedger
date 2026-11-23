#!/usr/bin/env bash
BT=/mnt/c/Users/llyyt/AppData/Local/DayFlowBuild/sdk/build-tools/35.0.0
APK="/mnt/c/Users/llyyt/Desktop/项目文件/MiniLedger/dist/miniledger.apk"
echo "=== badging ==="
"$BT/aapt2" dump badging "$APK" | head -16
echo "=== manifest pieces ==="
"$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK" \
  | grep -E "E: (service|receiver|activity)|A: android:name|foregroundServiceType|uses-permission|android.intent.action" \
  | sed 's/^ *//' | head -60
