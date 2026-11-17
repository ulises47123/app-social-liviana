#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"; cd "$RAIZ"
TAREA="assembleDebug"; [ "${1:-}" = release ] && TAREA="assembleRelease"
./gradlew --console=plain --offline "$TAREA"
