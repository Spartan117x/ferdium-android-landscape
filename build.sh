#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

ANDROID_JAR="/home/linuxmint/Android/Sdk/platforms/android-34/android.jar"
BUILD_TOOLS="/home/linuxmint/Android/Sdk/build-tools/34.0.0"
KOTLIN_STDLIB="/opt/kotlinc/lib/kotlin-stdlib.jar"
KEYSTORE="$HOME/.android/debug.keystore"
CACHE_DIR="$DIR/.build_cache"

mkdir -p "$CACHE_DIR"

echo "=== 1. Verificando debug keystore ==="
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$HOME/.android"
    keytool -genkey -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

echo "=== 2. Preparando directorios ==="
rm -rf build/apk build/gen build/compiled_res build/classes build/dex
mkdir -p build/gen build/compiled_res build/classes build/dex build/apk

echo "=== 3. Compilando recursos con aapt2 ==="
aapt2 compile --dir app/src/main/res -o build/compiled_res/resources.zip

echo "=== 4. Enlazando APK y generando R.java ==="
aapt2 link \
    -I "$ANDROID_JAR" \
    --manifest app/src/main/AndroidManifest.xml \
    -o build/apk/base.apk \
    --java build/gen \
    --auto-add-overlay \
    build/compiled_res/resources.zip

echo "=== 5. Compilando Kotlin con kotlinc ==="
kotlinc \
    -cp "$ANDROID_JAR:$DIR/libs/androidx-webkit.jar:$DIR/libs/androidx-annotation.jar" \
    -d build/classes \
    app/src/main/java/com/hiram/ferdium/MainActivity.kt \
    build/gen/com/hiram/ferdium/R.java

echo "=== 6. Convirtiendo a DEX con d8 ==="
if [ ! -f "$CACHE_DIR/kotlin-stdlib.dex" ]; then
    echo "  (Pre-indexando kotlin-stdlib para acelerar builds futuros...)"
    "$BUILD_TOOLS/d8" "$KOTLIN_STDLIB" --output "$CACHE_DIR/" --lib "$ANDROID_JAR" --min-api 26
    mv "$CACHE_DIR/classes.dex" "$CACHE_DIR/kotlin-stdlib.dex"
fi
if [ ! -f "$CACHE_DIR/androidx-webkit.dex" ]; then
    echo "  (Pre-indexando androidx-webkit...)"
    "$BUILD_TOOLS/d8" "$DIR/libs/androidx-webkit.jar" "$DIR/libs/androidx-annotation.jar" --output "$CACHE_DIR/" --lib "$ANDROID_JAR" --min-api 26
    mv "$CACHE_DIR/classes.dex" "$CACHE_DIR/androidx-webkit.dex"
fi

"$BUILD_TOOLS/d8" \
    $(find build/classes -name "*.class") \
    "$CACHE_DIR/kotlin-stdlib.dex" \
    "$CACHE_DIR/androidx-webkit.dex" \
    --output build/dex/ \
    --lib "$ANDROID_JAR" \
    --min-api 26

echo "=== 7. Empaquetando classes.dex en APK ==="
cd build/dex
zip -u "$DIR/build/apk/base.apk" classes*.dex
cd "$DIR"

echo "=== 8. Alineando con zipalign ==="
zipalign -p -f 4 build/apk/base.apk build/apk/ferdium-aligned.apk

echo "=== 9. Firmando con apksigner ==="
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out ferdium-landscape.apk \
    build/apk/ferdium-aligned.apk

echo "=== ¡COMPILACIÓN EXITOSA! ==="
echo "APK final: $DIR/ferdium-landscape.apk"
ls -lh "$DIR/ferdium-landscape.apk"
