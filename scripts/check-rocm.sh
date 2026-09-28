#!/usr/bin/env bash
set -u

echo "ROCm environment diagnostic (no GPU test is performed)"
for command in rocminfo amd-smi; do
  if command -v "$command" >/dev/null 2>&1; then
    echo "[found] $command: $(command -v "$command")"
  else
    echo "[missing] $command"
  fi
done

if [[ -e /dev/kfd ]]; then
  echo "[found] /dev/kfd"
else
  echo "[missing] /dev/kfd"
fi

found=0
for library in "${ROCM_PATH:-/opt/rocm}/lib/libamdhip64.so" /opt/rocm/lib64/libamdhip64.so; do
  if [[ -e "$library" ]]; then
    echo "[found] $library"
    found=1
  fi
done
if command -v ldconfig >/dev/null 2>&1 && ldconfig -p 2>/dev/null | grep -q 'libamdhip64\.so'; then
  echo "[found] libamdhip64.so in the dynamic linker cache"
  found=1
fi
if [[ $found -eq 0 ]]; then
  echo "[missing] libamdhip64.so in standard locations"
fi
