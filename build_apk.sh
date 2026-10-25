#!/usr/bin/env bash
# ============================================================
# 打安卓包（在 Git Bash 里跑）：./build_apk.sh [debug|release]
#   默认 release。产物：app/build/outputs/apk/<type>/
#
# 与旧壳工程的区别：这里是纯 Kotlin + Compose 原生工程，
# 没有 cap sync、没有 node_modules，Gradle 直接出包。
# ============================================================
set -e
cd "$(dirname "$0")"

TYPE="${1:-debug}"
SDK="D:/Android/Sdk"
APK_DIR="app/build/outputs/apk/$TYPE"

# ---- 1. 找 JDK 21（Studio 自带的 jbr 是 JDK 25，Gradle 8.14.3 不支持）----
find_jdk21() {
  local c
  for c in \
    "$JAVA_HOME" \
    /d/jdk21 \
    /c/Users/An/.jdks/*21* \
    "/c/Program Files/Java"/*21* \
    "/c/Program Files/Eclipse Adoptium"/*21* \
    "/c/Program Files/Microsoft"/*jdk-21* ; do
    [ -n "$c" ] && [ -x "$c/bin/java.exe" ] && { echo "$c"; return; }
  done
  for c in /c/Users/An/.gradle/jdks/* /c/Users/An/AppData/Local/Google/AndroidStudio*/jdk/* ; do
    if [ -x "$c/bin/java.exe" ] && "$c/bin/java.exe" -version 2>&1 | grep -q '"21'; then
      echo "$c"; return
    fi
  done
}
JDK="$(find_jdk21 || true)"
if [ -z "$JDK" ]; then
  echo "✗ 没找到 JDK 21"
  exit 1
fi
echo "▸ JDK21: $JDK"

# ---- 2. 前置检查 ----
[ -d "$SDK" ] || { echo "✗ 找不到 SDK: $SDK"; exit 1; }
[ -f local.properties ] || echo "sdk.dir=$SDK" > local.properties
[ -f keystore.properties ] || { [ "$TYPE" = release ] && { echo "✗ 缺 keystore.properties，release 没法签名"; exit 1; }; }

# ---- 3. 编译 ----
echo "▸ gradlew assemble${TYPE^}"
JAVA_HOME="$JDK" ./gradlew --no-daemon "assemble${TYPE^}" 2>&1 | tail -40

# ---- 4. 产物 ----
APK="$(ls -t "$APK_DIR"/*.apk 2>/dev/null | head -1)"
if [ -z "$APK" ]; then echo "✗ 没找到 APK（看上面日志）"; exit 1; fi
echo
echo "✓ APK: $APK  ($(du -h "$APK" | cut -f1))"

APKSIGNER="$(ls -t "$SDK"/build-tools/*/apksigner.bat 2>/dev/null | head -1)"
if [ -n "$APKSIGNER" ] && [ "$TYPE" = release ]; then
  echo "▸ 签名指纹（备案登记的就是这几个，不能变）"
  JAVA_HOME="$JDK" "$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | grep -Ei "MD5|SHA-1|SHA-256|DN" | sed 's/^/  /'
fi
