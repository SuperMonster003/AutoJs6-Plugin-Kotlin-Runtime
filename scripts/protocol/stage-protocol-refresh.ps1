[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$HostRepository,

    [string]$ExpectedRevision,

    [string]$OutputDirectory,

    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& git @Arguments)
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
    return $output
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

$pluginRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$lockPath = Join-Path $pluginRoot "protocol\protocol-artifacts.lock.json"
$lock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
if ($lock.schemaVersion -ne 2) {
    throw "Unsupported protocol lock schema: $($lock.schemaVersion)"
}
if ($lock.sourceDirty -ne $false) {
    throw "The current protocol lock does not describe a clean host source"
}

$hostRoot = (Resolve-Path -LiteralPath $HostRepository).Path
$insideWorkTree = (Invoke-Git -Arguments @("-C", $hostRoot, "rev-parse", "--is-inside-work-tree")) -join ""
if ($insideWorkTree.Trim() -ne "true") {
    throw "HostRepository is not a Git worktree: $hostRoot"
}

$lockedRevision = [string]$lock.sourceRevision
if ([string]::IsNullOrWhiteSpace($ExpectedRevision)) {
    $ExpectedRevision = $lockedRevision
}
if ($ExpectedRevision -notmatch "^[0-9a-f]{40}$") {
    throw "ExpectedRevision must be a full lowercase Git commit"
}

$hostRevision = ((Invoke-Git -Arguments @("-C", $hostRoot, "rev-parse", "HEAD")) -join "").Trim()
if ($hostRevision -ne $ExpectedRevision) {
    throw "Host HEAD $hostRevision differs from expected revision $ExpectedRevision"
}
$statusBefore = @(Invoke-Git -Arguments @(
    "-C",
    $hostRoot,
    "status",
    "--porcelain=v1",
    "--untracked-files=all"
))
if ($statusBefore.Count -ne 0) {
    throw "Host worktree must be clean before protocol generation"
}

$sourceTasks = @($lock.artifacts | ForEach-Object { [string]$_.sourceTask } | Select-Object -Unique)
if (-not $SkipBuild) {
    $gradleWrapper = Join-Path $hostRoot "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw "Host Gradle wrapper is missing: $gradleWrapper"
    }
    & $gradleWrapper @sourceTasks --offline --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Protocol AAR generation failed with exit code $LASTEXITCODE"
    }
}

$statusAfter = @(Invoke-Git -Arguments @(
    "-C",
    $hostRoot,
    "status",
    "--porcelain=v1",
    "--untracked-files=all"
))
if ($statusAfter.Count -ne 0) {
    throw "Host worktree became dirty during protocol generation"
}

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $pluginRoot "build\protocol-refresh\$($hostRevision.Substring(0, 12))"
}
$stagingPath = [System.IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $stagingPath) {
    $existing = @(Get-ChildItem -LiteralPath $stagingPath -Force)
    if ($existing.Count -ne 0) {
        throw "Refusing to overwrite non-empty staging directory: $stagingPath"
    }
} else {
    New-Item -ItemType Directory -Path $stagingPath | Out-Null
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$hostPrefix = $hostRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) +
    [System.IO.Path]::DirectorySeparatorChar
$artifactRows = foreach ($artifact in $lock.artifacts) {
    $sourcePath = [System.IO.Path]::GetFullPath(
        (Join-Path $hostRoot ([string]$artifact.sourceOutput).Replace("/", "\"))
    )
    if (-not $sourcePath.StartsWith($hostPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Protocol source output escapes the host repository: $sourcePath"
    }
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Generated protocol AAR is missing: $sourcePath"
    }

    $zip = [System.IO.Compression.ZipFile]::OpenRead($sourcePath)
    try {
        if ($null -eq $zip.GetEntry("classes.jar")) {
            throw "Generated protocol AAR has no classes.jar: $sourcePath"
        }
    } finally {
        $zip.Dispose()
    }

    $stagedPath = Join-Path $stagingPath ([string]$artifact.file)
    Copy-Item -LiteralPath $sourcePath -Destination $stagedPath
    $pinnedPath = Join-Path $pluginRoot "protocol\$([string]$artifact.file)"
    $generatedHash = Get-Sha256 -Path $stagedPath
    $pinnedHash = Get-Sha256 -Path $pinnedPath
    [ordered]@{
        file = [string]$artifact.file
        sourceModule = [string]$artifact.sourceModule
        sourceTask = [string]$artifact.sourceTask
        sourceOutput = [string]$artifact.sourceOutput
        generatedBytes = (Get-Item -LiteralPath $stagedPath).Length
        generatedSha256 = $generatedHash
        pinnedBytes = (Get-Item -LiteralPath $pinnedPath).Length
        pinnedSha256 = $pinnedHash
        exactMatch = $generatedHash -eq $pinnedHash
    }
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    sourceRepository = "AutoJs6"
    sourceRevision = $hostRevision
    sourceDirty = $false
    artifactVariant = [string]$lock.artifactVariant
    offline = $true
    buildSkipped = [bool]$SkipBuild
    artifacts = @($artifactRows)
    promotionPerformed = $false
}
$reportPath = Join-Path $stagingPath "refresh-report.json"
[System.IO.File]::WriteAllText(
    $reportPath,
    (($report | ConvertTo-Json -Depth 6) + [Environment]::NewLine),
    [System.Text.UTF8Encoding]::new($false)
)

$report | ConvertTo-Json -Depth 6

