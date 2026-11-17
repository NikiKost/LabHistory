#!/usr/bin/env bash
set -euo pipefail

release_dir="${1:?Release directory is required}"
release_tag="${2:?Release tag is required}"
: "${YANDEX_DISK_TOKEN:?YANDEX_DISK_TOKEN is required}"

api_url="https://cloud-api.yandex.net/v1/disk/resources"
remote_release_dir="app:/releases/${release_tag}"
response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

create_directory() {
  local remote_path="$1"
  local status
  status="$(curl \
    --silent \
    --show-error \
    --output "$response_file" \
    --write-out '%{http_code}' \
    --request PUT \
    --header "Authorization: OAuth ${YANDEX_DISK_TOKEN}" \
    --get \
    --data-urlencode "path=${remote_path}" \
    "$api_url")"

  if [[ "$status" != "201" && "$status" != "409" ]]; then
    echo "Could not create Yandex Disk directory ${remote_path} (HTTP ${status})" >&2
    cat "$response_file" >&2
    return 1
  fi
}

upload_file() {
  local local_path="$1"
  local remote_path="$2"
  local upload_response
  local upload_url

  test -f "$local_path"
  upload_response="$(curl \
    --silent \
    --show-error \
    --fail-with-body \
    --request GET \
    --header "Authorization: OAuth ${YANDEX_DISK_TOKEN}" \
    --get \
    --data-urlencode "path=${remote_path}" \
    --data-urlencode 'overwrite=true' \
    "${api_url}/upload")"
  upload_url="$(jq -er '.href' <<< "$upload_response")"

  curl \
    --silent \
    --show-error \
    --fail-with-body \
    --request PUT \
    --upload-file "$local_path" \
    "$upload_url" \
    >/dev/null
  echo "Uploaded $(basename "$local_path") to ${remote_release_dir}"
}

apk_name="LabHistory-${release_tag}.apk"
create_directory 'app:/releases'
create_directory "$remote_release_dir"
upload_file "${release_dir}/${apk_name}" "${remote_release_dir}/${apk_name}"
upload_file "${release_dir}/${apk_name}.sha256" "${remote_release_dir}/${apk_name}.sha256"
upload_file "${release_dir}/update.json" "${remote_release_dir}/update.json"
