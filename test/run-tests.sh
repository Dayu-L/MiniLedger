#!/usr/bin/env bash
# 纯 JVM 跑 agent / parser 自测，不需要 Android 环境
set -euo pipefail
B=/mnt/c/Users/llyyt/AppData/Local/DayFlowBuild
JDK="$B/jdk/bin"
PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$PROJ/app/src/main/java/com/miniledger/app"
OUT=/tmp/ledger-test

rm -rf "$OUT"
mkdir -p "$OUT"

echo "=== ParserTest ==="
"$JDK/javac" -encoding UTF-8 -d "$OUT" \
  "$SRC/Tx.java" "$SRC/Money.java" "$SRC/Parser.java" "$PROJ/test/ParserTest.java"
"$JDK/java" -Dfile.encoding=UTF-8 -cp "$OUT" com.miniledger.app.ParserTest

echo
echo "=== AgentTest ==="
"$JDK/javac" -encoding UTF-8 -d "$OUT" \
  "$SRC/Tx.java" "$SRC/Money.java" "$SRC/Agent.java" "$PROJ/test/AgentTest.java"
"$JDK/java" -Dfile.encoding=UTF-8 -cp "$OUT" com.miniledger.app.AgentTest

echo
echo "=== SpanTest ==="
"$JDK/javac" -encoding UTF-8 -d "$OUT" \
  "$SRC/Span.java" "$PROJ/test/SpanTest.java"
"$JDK/java" -Dfile.encoding=UTF-8 -cp "$OUT" com.miniledger.app.SpanTest

echo
echo "=== RecurTest ==="
AJ="$B/sdk/platforms/android-35/android.jar"
"$JDK/javac" -encoding UTF-8 -cp "$AJ" -d "$OUT" \
  "$SRC/Span.java" "$SRC/Tx.java" "$SRC/Money.java" "$SRC/Categories.java" "$SRC/Db.java" \
  "$SRC/Recur.java" "$PROJ/test/RecurTest.java"
"$JDK/java" -Dfile.encoding=UTF-8 -cp "$OUT:$AJ" com.miniledger.app.RecurTest

echo
echo "=== DupeTest ==="
"$JDK/javac" -encoding UTF-8 -d "$OUT" \
  "$SRC/Span.java" "$SRC/Tx.java" "$SRC/Money.java" "$PROJ/test/DupeTest.java" \
  "$SRC/Dupe.java"
"$JDK/java" -Dfile.encoding=UTF-8 -cp "$OUT" com.miniledger.app.DupeTest
