#!/bin/sh
# TDLib precompilado (io.github.tdlib-android:core:0.1.1, Maven Central)
set -e
curl -sLo /tmp/core.aar https://repo1.maven.org/maven2/io/github/tdlib-android/core/0.1.1/core-0.1.1.aar
mkdir -p /tmp/tdaar && cd /tmp/tdaar && unzip -o -q /tmp/core.aar && cd - >/dev/null
cp /tmp/tdaar/libs/tdlib-classes.jar app/libs/
for a in armeabi-v7a arm64-v8a; do mkdir -p app/src/main/jniLibs/$a; cp /tmp/tdaar/jni/$a/libtdjni.so app/src/main/jniLibs/$a/; done
