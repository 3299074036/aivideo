#!/usr/bin/env bash
# 拉取 sherpa-onnx Android AAR（端侧 ASR/TTS 用），带 SHA-256 校验。
# 用法：在仓库根目录执行 bash libs/fetch-sherpa-onnx.sh
set -euo pipefail

VERSION="1.13.8"
AAR_NAME="sherpa-onnx-${VERSION}.aar"
SHA256="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${AAR_NAME}"
OUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${OUT_DIR}/${AAR_NAME}"

if [ -f "$OUT" ]; then
    echo "已存在：$OUT，跳过下载"
else
    echo "下载 $URL ..."
    curl -sSL -o "$OUT" "$URL"
fi

echo "校验 SHA-256 ..."
ACTUAL="$(sha256sum "$OUT" | awk '{print $1}')"
if [ "$ACTUAL" != "$SHA256" ]; then
    echo "校验失败：期望 $SHA256，实际 $ACTUAL" >&2
    rm -f "$OUT"
    exit 1
fi
echo "校验通过：$OUT"
