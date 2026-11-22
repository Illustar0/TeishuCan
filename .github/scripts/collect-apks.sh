#!/usr/bin/env bash
# 把 Gradle 产出的 release APK 复制到 $1，按 TeishuCan-<versionName>-<abi>.apk 命名，
# 并把 versionName 写入 GitHub Actions 的 step 输出 version。
set -euo pipefail

output_dir="$1"
apk_dir=app/build/outputs/apk/release
metadata="$apk_dir/output-metadata.json"

mkdir -p "$output_dir"
version="$(jq -r '.elements[0].versionName' "$metadata")"

jq -r '.elements[] | [.outputFile, ((.filters[] | select(.filterType == "ABI") | .value) // "universal")] | @tsv' "$metadata" |
  while IFS=$'\t' read -r apk abi; do
    if [[ "$apk" == *-unsigned.apk ]]; then
      echo "::error::$apk is unsigned; check the release signing environment variables."
      exit 1
    fi
    cp "$apk_dir/$apk" "$output_dir/TeishuCan-$version-$abi.apk"
  done

echo "version=$version" >> "$GITHUB_OUTPUT"
