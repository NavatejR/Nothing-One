#!/usr/bin/env bash
#
# Fetches the on-device AI models into app/src/main/assets/ai/.
#
#   gemma3-1b-it-int4.task  (~529 MB)  Gemma 3 1B IT, int4 — assistant
#   ggml-tiny.en.bin        (~74 MB)   whisper tiny.en — dictation
#
# The Gemma .task bundle must be the MediaPipe/Google AI Edge packaged
# variant (from ai.google.dev/edge/gemma), not the raw safetensors.
#
# Usage: ./scripts/fetch-models.sh
set -euo pipefail

GEMMA_URL="${GEMMA_URL:-https://storage.googleapis.com/mediapipe-models/gemma/gemma3-1b-it-int4/gemma3-1b-it-int4.task}"
WHISPER_URL="${WHISPER_URL:-https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin}"

DEST="$(dirname "$0")/../app/src/main/assets/ai"
mkdir -p "$DEST"

fetch() {
  local url="$1" out="$2" size="$3"
  if [[ -s "$out" ]]; then
    echo "✓ $(basename "$out") already present"
    return 0
  fi
  echo "↓ $(basename "$out") ($size)…"
  curl -L --fail --progress-bar "$url" -o "$out.part"
  mv "$out.part" "$out"
  echo "✓ $(basename "$out") done"
}

fetch "$GEMMA_URL" "$DEST/gemma3-1b-it-int4.task" "~529 MB"
fetch "$WHISPER_URL" "$DEST/ggml-tiny.en.bin" "~74 MB"

echo
echo "Models ready. Rebuild the APK to bundle them:"
echo "  ./gradlew assembleDebug"
