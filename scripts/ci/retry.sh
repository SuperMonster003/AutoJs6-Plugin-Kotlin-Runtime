#!/usr/bin/env bash

set -uo pipefail

if (( $# == 0 )); then
  echo "usage: retry.sh <command> [args ...]" >&2
  exit 64
fi

max_attempts="${RETRY_MAX_ATTEMPTS:-3}"
base_delay_seconds="${RETRY_DELAY_SECONDS:-10}"
if [[ ! "$max_attempts" =~ ^[1-9][0-9]*$ || ! "$base_delay_seconds" =~ ^[0-9]+$ ]]; then
  echo "retry limits must be non-negative integers and max attempts must be positive" >&2
  exit 64
fi

for (( attempt = 1; attempt <= max_attempts; attempt++ )); do
  echo "Attempt $attempt/$max_attempts: $*"
  "$@"
  status=$?
  if (( status == 0 )); then
    exit 0
  fi
  if (( attempt == max_attempts )); then
    echo "Command failed after $max_attempts attempts (exit $status): $*" >&2
    exit "$status"
  fi
  sleep_seconds=$(( base_delay_seconds * attempt ))
  echo "Command exited $status; retrying in ${sleep_seconds}s" >&2
  sleep "$sleep_seconds"
done
