#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_JAR:?Set ANDROID_JAR to Android SDK 35 android.jar}"
: "${BUILD_TOOLS:?Set BUILD_TOOLS to Android build-tools 35 directory}"
: "${ECJ_JAR:?Set ECJ_JAR to Eclipse compiler JAR (3.39.0)}"
mkdir -p build/classes build/dex build/gen
rm -f build/classes-list.txt build/unsigned.apk build/aligned.apk
find build/classes -type f -delete
"$BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$BUILD_TOOLS/aapt2" link -o build/unsigned.apk -I "$ANDROID_JAR" --manifest app/src/main/AndroidManifest.xml build/resources.zip --min-sdk-version 26 --target-sdk-version 35 --version-code 1 --version-name 1.0 --java build/gen
java -jar "$ECJ_JAR" -source 1.8 -target 1.8 -proc:none -nowarn -bootclasspath "$ANDROID_JAR:$BUILD_TOOLS/core-lambda-stubs.jar" -d build/classes app/src/main/java/id/fyland/*.java $(find build/gen -name R.java)
find build/classes -name '*.class' > build/classes-list.txt
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 26 --output build/dex @build/classes-list.txt
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 z.write('build/dex/classes.dex','classes.dex')
PY
"$BUILD_TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
# Signing: pakai KEYSTORE/KS_ALIAS/KS_PASS kalau di-set; kalau tidak, buat kunci debug sementara.
if [ -z "${KEYSTORE:-}" ]; then
  KEYSTORE=build/debug.jks; KS_ALIAS=debug; KS_PASS=android
  [ -f "$KEYSTORE" ] || keytool -genkeypair -keystore "$KEYSTORE" -alias "$KS_ALIAS" -storepass "$KS_PASS" -keypass "$KS_PASS" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fyland Debug" >/dev/null
fi
: "${KS_ALIAS:?Set KS_ALIAS}"; : "${KS_PASS:?Set KS_PASS}"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" --ks-pass env:KS_PASS --key-pass env:KS_PASS --out build/Fyland-1.0.apk build/aligned.apk
"$BUILD_TOOLS/apksigner" verify --verbose build/Fyland-1.0.apk
