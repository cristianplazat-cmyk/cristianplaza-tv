#!/bin/zsh
# Compila "Cristian Plaza TV" (sin Gradle) y deja el APK firmado en dist/.
# Uso:  ./build.sh
set -e
cd "${0:A:h}"
VERSION=1.0.2
CODIGO=3

export JAVA_HOME=${JAVA_HOME:-$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home}
export PATH=$JAVA_HOME/bin:$PATH
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar

rm -rf build && mkdir -p build/gen build/classes build/dex dist
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 28 --target-sdk-version 34 \
  --version-code $CODIGO --version-name $VERSION \
  --java build/gen -o build/unsigned.apk build/res.zip
javac --release 11 -Xlint:-options -classpath "$JAR" -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 28 --release --output build/dex $(find build/classes -name '*.class')
cp build/unsigned.apk build/with-dex.apk
(cd build/dex && zip -q ../with-dex.apk classes.dex)
"$BT/zipalign" -f 4 build/with-dex.apk build/aligned.apk

# Firma de publicación. ¡Respalda la carpeta firma/! Sin ella no podrás publicar actualizaciones.
KS=firma/cristianplaza-tv.jks
CLAVE_ARCHIVO=firma/clave.txt
if [ ! -f $KS ]; then
  openssl rand -base64 24 | tr -d '/+=' > $CLAVE_ARCHIVO
  keytool -genkeypair -keystore $KS -storepass "$(cat $CLAVE_ARCHIVO)" -keypass "$(cat $CLAVE_ARCHIVO)" \
    -alias cristianplaza -keyalg RSA -keysize 4096 -validity 36500 \
    -dname "CN=Cristian Plaza TV" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks $KS --ks-pass file:$CLAVE_ARCHIVO --out dist/CristianPlazaTV-$VERSION.apk build/aligned.apk
"$BT/apksigner" verify dist/CristianPlazaTV-$VERSION.apk && echo "Firma OK"
ls -la dist/
