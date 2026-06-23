#!/bin/bash
set -e

PROJECT_DIR=$(pwd)
ANDROID_JAR="$PROJECT_DIR/android.jar"
DOCUMENTFILE_JAR="$PROJECT_DIR/documentfile/classes.jar"
R8_JAR="$PROJECT_DIR/r8.jar"
RES_DIR="$PROJECT_DIR/res"
MANIFEST="$PROJECT_DIR/AndroidManifest.xml"
SRC_DIR="$PROJECT_DIR/src"
GEN_DIR="$PROJECT_DIR/gen"
OBJ_DIR="$PROJECT_DIR/obj"
BIN_DIR="$PROJECT_DIR/bin"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "❌ android.jar not found – please place it in the project folder"
    exit 1
fi
echo "✅ android.jar ready"

for jar in "$DOCUMENTFILE_JAR" "$R8_JAR"; do
    [ -f "$jar" ] || { echo "❌ Missing $jar"; exit 1; }
done
echo "✅ All tools ready"

rm -rf "$GEN_DIR" "$OBJ_DIR" "$BIN_DIR"
mkdir -p "$GEN_DIR" "$OBJ_DIR" "$BIN_DIR"

echo "📦 Compiling resources..."
aapt2 compile -o "$GEN_DIR/compiled.zip" --dir "$RES_DIR"
aapt2 link -I "$ANDROID_JAR" --manifest "$MANIFEST" --java "$GEN_DIR" \
    -o "$BIN_DIR/resources.apk" "$GEN_DIR/compiled.zip"

OLD_R=$(find "$SRC_DIR" -name "R.java" 2>/dev/null | head -1)
[ -n "$OLD_R" ] && rm -f "$OLD_R"

echo "☕ Compiling Java sources..."
javac -d "$OBJ_DIR" \
    -classpath "$ANDROID_JAR:$DOCUMENTFILE_JAR" \
    -source 1.8 -target 1.8 -Xlint:-options \
    $(find "$SRC_DIR" -name "*.java") \
    $(find "$GEN_DIR" -name "*.java" 2>/dev/null || true)

echo "🧩 Dexing with R8..."
DEX_JAR="$GEN_DIR/classes.jar"
java -Xmx1024m -cp "$R8_JAR" com.android.tools.r8.D8 \
    --lib "$ANDROID_JAR" \
    --min-api 21 \
    --classpath "$ANDROID_JAR" \
    --classpath "$DOCUMENTFILE_JAR" \
    --output "$DEX_JAR" \
    $(find "$OBJ_DIR" -name "*.class") \
    "$DOCUMENTFILE_JAR"

echo "📦 Packaging APK..."
cp "$BIN_DIR/resources.apk" "$BIN_DIR/app-unsigned.apk"
unzip -o "$DEX_JAR" "*.dex" -d "$BIN_DIR"
cd "$BIN_DIR"
for f in *.dex; do
    zip -q app-unsigned.apk "$f"
done
cd "$PROJECT_DIR"

echo "🔑 Signing APK..."
SIGN_KEYSTORE="$PROJECT_DIR/webserver_signing.keystore"
if [ ! -f "$SIGN_KEYSTORE" ]; then
    echo "❌ Signing keystore not found at $SIGN_KEYSTORE"
    echo "Generate one with: keytool -genkey -v -keystore $SIGN_KEYSTORE ..."
    exit 1
fi

apksigner sign --ks "$SIGN_KEYSTORE" --ks-type PKCS12 \
    --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias server \
    --out "$BIN_DIR/app-debug.apk" "$BIN_DIR/app-unsigned.apk"

TARGET="/sdcard"
[ -d "$TARGET" ] && [ -w "$TARGET" ] || TARGET="/storage/emulated/0"

if [ -d "$TARGET" ] && [ -w "$TARGET" ]; then
    cp "$BIN_DIR/app-debug.apk" "$TARGET/app-debug.apk"
    echo "✅ APK copied to $TARGET/app-debug.apk"
else
    echo "❌ Cannot write to internal storage ($TARGET)"
    exit 1
fi
