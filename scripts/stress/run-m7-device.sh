#!/usr/bin/env bash
set -euo pipefail

serial="${1:-${ANDROID_SERIAL:-QV710AF65F}}"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../.." && pwd)"
stamp="$(date +%Y%m%d-%H%M%S)"
evidence_dir="${M7_EVIDENCE_DIR:-$repo_root/build/m7-evidence/$stamp}"
release_apk="$repo_root/app/build/outputs/apk/release/app-release.apk"
debug_apk="$repo_root/app/build/outputs/apk/debug/app-debug.apk"
harness_apk="$repo_root/m7-harness/build/outputs/apk/debug/m7-harness-debug.apk"
provider_package="io.github.supermonster003.autojs6.plugin.kotlin.runtime"
harness_package="org.autojs.plugin.jvmsource.kotlin.m7harness"
test_class="org.autojs.plugin.jvmsource.kotlin.m7harness.M7ProviderHarnessInstrumentedTest"
runner="$harness_package/androidx.test.runner.AndroidJUnitRunner"

mkdir -p "$evidence_dir"
cd "$repo_root"

restore_release() {
  if [[ -f "$release_apk" ]]; then
    adb -s "$serial" install -r "$release_apk" >/dev/null || true
  fi
}
trap restore_release EXIT

wait_process_absent() {
  local process_name="$1"
  local attempt pid_text
  for attempt in $(seq 1 25); do
    pid_text="$(adb -s "$serial" shell pidof "$process_name" 2>/dev/null | tr -d '\r' || true)"
    [[ -z "$pid_text" ]] && return 0
    sleep 0.2
  done
  echo "Process remained after provider unbind: $process_name" >&2
  return 1
}

run_harness_test() {
  local method="$1"
  local run_id="$2"
  local evidence_prefix="$3"
  local output_file="$evidence_dir/$run_id.instrumentation.txt"
  local log_file="$evidence_dir/$run_id.logcat.txt"
  local status

  adb -s "$serial" logcat -c
  set +e
  adb -s "$serial" shell am instrument -w -r \
    -e m7RunId "$run_id" \
    -e class "$test_class#$method" \
    "$runner" 2>&1 | tee "$output_file"
  status="${PIPESTATUS[0]}"
  set -e
  [[ "$status" -eq 0 ]]
  grep -Fq "OK (1 test)" "$output_file"
  grep -Fq "INSTRUMENTATION_CODE: -1" "$output_file"
  ! grep -Eq "FAILURES!!!|Process crashed" "$output_file"

  adb -s "$serial" logcat -d -v raw -s M7Harness:I '*:S' >"$log_file"
  grep -F "$evidence_prefix" "$log_file"
  wait_process_absent "$provider_package:worker"
  wait_process_absent "$provider_package:compiler"
}

adb -s "$serial" get-state >/dev/null
if [[ "${M7_SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew \
    :app:assembleRelease \
    :app:assembleDebug \
    :m7-harness:assembleDebug \
    --offline \
    --console=plain
fi
for apk in "$release_apk" "$debug_apk" "$harness_apk"; do
  [[ -f "$apk" ]] || { echo "Required APK is missing: $apk" >&2; exit 1; }
done

adb -s "$serial" install -r -t "$harness_apk"
adb -s "$serial" install -r "$release_apk"
run_harness_test benchmarkFiveCacheColdAndWarmPairs "m7-benchmark-$stamp" M7_BENCHMARK_EVIDENCE=
run_harness_test fiftySequentialSessionsRetireEveryWorker "m7-stress-release-$stamp" M7_STRESS_EVIDENCE=
run_harness_test oversizedSourceIsRejectedAtInput "m7-fault-oversize-$stamp" M7_FAULT_EVIDENCE=
run_harness_test compileStarvationTimesOutAtCompilation "m7-fault-compile-timeout-$stamp" M7_FAULT_EVIDENCE=
run_harness_test executionInfiniteLoopTimesOutAndIsKilled "m7-fault-execution-timeout-$stamp" M7_FAULT_EVIDENCE=

adb -s "$serial" install -r "$debug_apk"
if [[ "${M7_SKIP_RESOURCE_AUDIT:-0}" != "1" ]]; then
  run_harness_test fiftySequentialSessionsRetireEveryWorker "m7-stress-resource-$stamp" M7_STRESS_EVIDENCE=
fi
run_harness_test externalWorkerKillIsReported "m7-fault-external-kill-$stamp" M7_FAULT_EVIDENCE=

restore_release
trap - EXIT
echo "M7 device evidence: $evidence_dir"
