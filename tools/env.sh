#!/usr/bin/env bash
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HERRAMIENTAS="${HERRAMIENTAS:-/c/Users/Usuario/Music/proyectos/app-mando-facil/.tools}"
export JAVA_HOME="$HERRAMIENTAS/jdk"
export ANDROID_HOME="$HERRAMIENTAS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
