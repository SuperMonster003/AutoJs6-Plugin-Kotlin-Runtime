[CmdletBinding()]
param(
    [string]$Serial = "QV710AF65F",
    [string]$EvidenceDirectory,
    [switch]$SkipBuild,
    [switch]$SkipResourceAudit
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$adb = (Get-Command adb -ErrorAction Stop).Source
$gradle = Join-Path $repoRoot "gradlew.bat"
$releaseApk = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
$debugApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$harnessApk = Join-Path $repoRoot "m7-harness\build\outputs\apk\debug\m7-harness-debug.apk"
$providerPackage = "io.github.supermonster003.autojs6.plugin.kotlin.runtime"
$harnessPackage = "org.autojs.plugin.jvmsource.kotlin.m7harness"
$testClass = "org.autojs.plugin.jvmsource.kotlin.m7harness.M7ProviderHarnessInstrumentedTest"
$runner = "$harnessPackage/androidx.test.runner.AndroidJUnitRunner"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $EvidenceDirectory) {
    $EvidenceDirectory = Join-Path $repoRoot "build\m7-evidence\$stamp"
}
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null

function Invoke-Checked {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$ArgumentList
    )
    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $FilePath $($ArgumentList -join ' ')"
    }
}

function Wait-ProcessAbsent {
    param([Parameter(Mandatory)][string]$ProcessName)
    for ($attempt = 0; $attempt -lt 25; $attempt++) {
        $pidText = (& $adb -s $Serial shell pidof $ProcessName 2>$null | Out-String).Trim()
        if (-not $pidText) { return }
        Start-Sleep -Milliseconds 200
    }
    throw "Process remained after provider unbind: $ProcessName"
}

function Invoke-HarnessTest {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$RunId,
        [Parameter(Mandatory)][string]$EvidencePrefix
    )
    Invoke-Checked $adb @("-s", $Serial, "logcat", "-c")
    $arguments = @(
        "-s", $Serial, "shell", "am", "instrument", "-w", "-r",
        "-e", "m7RunId", $RunId,
        "-e", "class", "$testClass#$Method",
        $runner
    )
    $lines = @(& $adb @arguments 2>&1 | ForEach-Object { "$_" })
    $status = $LASTEXITCODE
    $text = $lines -join [Environment]::NewLine
    $text | Set-Content -LiteralPath (Join-Path $EvidenceDirectory "$RunId.instrumentation.txt") -Encoding utf8
    Write-Host $text
    if ($status -ne 0 -or $text -notmatch [regex]::Escape("OK (1 test)") -or
        $text -notmatch [regex]::Escape("INSTRUMENTATION_CODE: -1") -or
        $text -match "FAILURES!!!|Process crashed") {
        throw "Instrumentation failed: $testClass#$Method"
    }
    $logLines = @(& $adb -s $Serial logcat -d -v raw -s "M7Harness:I" "*:S" 2>&1 |
        ForEach-Object { "$_" })
    $logLines | Set-Content -LiteralPath (Join-Path $EvidenceDirectory "$RunId.logcat.txt") -Encoding utf8
    $evidence = @($logLines | Where-Object { $_.StartsWith($EvidencePrefix) })
    if ($evidence.Count -eq 0) {
        throw "Evidence prefix was not emitted: $EvidencePrefix"
    }
    $evidence | ForEach-Object { Write-Host $_ }
    Wait-ProcessAbsent "$providerPackage`:worker"
    Wait-ProcessAbsent "$providerPackage`:compiler"
}

Push-Location $repoRoot
try {
    Invoke-Checked $adb @("-s", $Serial, "get-state")
    if (-not $SkipBuild) {
        Invoke-Checked $gradle @(
            ":app:assembleRelease",
            ":app:assembleDebug",
            ":m7-harness:assembleDebug",
            "--offline",
            "--console=plain"
        )
    }
    @($releaseApk, $debugApk, $harnessApk) | ForEach-Object {
        if (-not (Test-Path -LiteralPath $_ -PathType Leaf)) { throw "Required APK is missing: $_" }
    }

    Invoke-Checked $adb @("-s", $Serial, "install", "-r", "-t", $harnessApk)
    Invoke-Checked $adb @("-s", $Serial, "install", "-r", $releaseApk)
    Invoke-HarnessTest "benchmarkFiveCacheColdAndWarmPairs" "m7-benchmark-$stamp" "M7_BENCHMARK_EVIDENCE="
    Invoke-HarnessTest "fiftySequentialSessionsRetireEveryWorker" "m7-stress-release-$stamp" "M7_STRESS_EVIDENCE="
    Invoke-HarnessTest "oversizedSourceIsRejectedAtInput" "m7-fault-oversize-$stamp" "M7_FAULT_EVIDENCE="
    Invoke-HarnessTest "compileStarvationTimesOutAtCompilation" "m7-fault-compile-timeout-$stamp" "M7_FAULT_EVIDENCE="
    Invoke-HarnessTest "executionInfiniteLoopTimesOutAndIsKilled" "m7-fault-execution-timeout-$stamp" "M7_FAULT_EVIDENCE="

    Invoke-Checked $adb @("-s", $Serial, "install", "-r", $debugApk)
    if (-not $SkipResourceAudit) {
        Invoke-HarnessTest "fiftySequentialSessionsRetireEveryWorker" "m7-stress-resource-$stamp" "M7_STRESS_EVIDENCE="
    }
    Invoke-HarnessTest "externalWorkerKillIsReported" "m7-fault-external-kill-$stamp" "M7_FAULT_EVIDENCE="
} finally {
    if (Test-Path -LiteralPath $releaseApk -PathType Leaf) {
        try {
            Invoke-Checked $adb @("-s", $Serial, "install", "-r", $releaseApk)
        } catch {
            Write-Warning "Unable to restore the release provider: $_"
        }
    }
    Pop-Location
}

Write-Host "M7 device evidence: $EvidenceDirectory"
