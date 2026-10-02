# Copycat Placer code-level self-test (one shot).
#
#   powershell -ExecutionPolicy Bypass -File tools/run-harness.ps1
#
# What it does:
#   1. writes the self-test command and "stop" into a stdin file;
#   2. starts a headless dedicated server through loom's own runServer task,
#      with stdin redirected from that file;
#   3. prints run/maris-placer-selftest.txt.
#
# Why this is a script and not a Gradle JavaExec task: dev-launch-injector needs the
# inherited working directory plus system properties to install the Knot class loader;
# launching the game straight from JavaExec dies with
# "trying to load FabricLoaderImpl from target class loader". gradlew runServer plus a
# shell level stdin redirect is stable because that is loom's own launch path.
#
# World isolation: runServer uses the run/1.20.1-fabric/ directory. This script rewrites
# server.properties so that level-name=harness and level-type=flat (every other key is
# preserved), so the self-test runs in its own superflat world and the development save
# under world/ is never touched.
#
# NOTE: this file is deliberately ASCII only. Windows PowerShell 5.1 reads .ps1 files as
# ANSI unless they carry a BOM, so a UTF-8 file with non-ASCII comments gets mangled and
# can even fail to parse. Keep every comment in this file in plain ASCII.

param([switch]$WithCopycats, [switch]$Offline)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $root
Set-Location $root

$stdin = Join-Path $root "tools\harness-server-stdin.txt"
$report = Join-Path $root "run\1.20.1-fabric\maris-placer-selftest.txt"
$console = Join-Path $root "build\harness-console.txt"

# 1. The self-test command. "stop" goes last: the self-test runs synchronously, so stop
#    is only reached after it finished.
[System.IO.File]::WriteAllText($stdin, "marisplacer self-test`nstop`n")

# 2. World isolation.
#    Escaping note: vanilla server.properties writes level-type as minecraft\:flat
#    (in the Java properties format a colon separates key and value, so it must be
#    backslash escaped). The script has to emit exactly one backslash plus a colon; an
#    extra backslash makes vanilla fail to recognise "flat" and fall back to a normal
#    world type.
$props = Join-Path $root "run\1.20.1-fabric\server.properties"
New-Item -ItemType Directory -Force (Split-Path -Parent $props) | Out-Null
$kept = @()
if (Test-Path $props) {
    $kept = Get-Content $props | Where-Object { $_.Trim() -ne "" -and -not $_.StartsWith("#") } |
            Where-Object { ($_ -split "=", 2)[0] -ne "level-name" -and ($_ -split "=", 2)[0] -ne "level-type" }
}
$flat = 'level-type=minecraft\:flat'
($kept + "level-name=harness", $flat) -join "`n" | Set-Content -Path $props -Encoding ascii

Remove-Item -Force $report -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Split-Path -Parent $console) | Out-Null

# 3. Run.
$extra = ""
if ($WithCopycats) { $extra += " -PwithCopycats" }
if ($Offline) { $extra += " --offline" }
cmd /c "chcp 65001 > nul && .\gradlew :1.20.1-fabric:runServer --no-daemon --console=plain$extra < tools\harness-server-stdin.txt > build\harness-console.txt 2>&1"
$gradleExit = $LASTEXITCODE

Write-Output "===== self-test report ====="
if (Test-Path $report) {
    Get-Content -Encoding UTF8 $report
} else {
    Write-Output "no report file; the server console is in build/harness-console.txt"
}

if ($gradleExit -ne 0 -or !(Test-Path $report) -or
    !((Get-Content -Raw -Encoding UTF8 $report) -match '\u7ed3\u679c\uff1a\u5168\u90e8\u901a\u8fc7')) {
    throw "Placement harness failed; see $console"
}
