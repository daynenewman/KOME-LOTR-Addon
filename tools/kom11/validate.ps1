param(
    [ValidateSet('Build', 'Native')][string]$Mode = 'Build',
    [string]$GradleUserHome = $env:GRADLE_USER_HOME
)
$ErrorActionPreference = 'Stop'
$validationRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidenceRoot = Join-Path $validationRoot 'build/kom11'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
if ([string]::IsNullOrWhiteSpace($GradleUserHome)) {
    $GradleUserHome = Join-Path $env:USERPROFILE '.gradle'
}
$env:GRADLE_USER_HOME = $GradleUserHome
$validationMutex = [System.Threading.Mutex]::new($false, 'Local\KOME-Heavy-Validation')
$acquired = $false
$validationExit = 1
Push-Location -LiteralPath $validationRoot
try {
    Write-Output 'Waiting for Local\KOME-Heavy-Validation'
    while (-not $acquired) {
        try { $acquired = $validationMutex.WaitOne(1000) }
        catch [System.Threading.AbandonedMutexException] { $acquired = $true }
    }
    Write-Output 'Acquired Local\KOME-Heavy-Validation'
    $validationArgs = @('test', 'build', '--no-daemon', '--offline', '--max-workers=2')
    $log = Join-Path $evidenceRoot 'tests-build.log'
    if ($Mode -eq 'Native') {
        $nativeResult = Join-Path $evidenceRoot 'forge/native-rosters.tsv'
        if (Test-Path -LiteralPath $nativeResult) { Remove-Item -LiteralPath $nativeResult }
        $validationArgs = @('runServer', '-I', 'tools/kom11/verify-native.gradle', '--no-daemon', '--offline', '--max-workers=2')
        $log = Join-Path $evidenceRoot 'forge.log'
    }
    & ./gradlew.bat @validationArgs 2>&1 | Tee-Object -FilePath $log
    $validationExit = $LASTEXITCODE
    if ($Mode -eq 'Native' -and $validationExit -eq 0) {
        if (-not (Test-Path -LiteralPath $nativeResult) -or
                -not (Select-String -LiteralPath $nativeResult -Pattern '^PASS factions=24 units=47; no entities spawned$' -Quiet)) {
            throw 'Forge exited without a passing native roster verification result.'
        }
    }
} finally {
    if ($acquired) { $validationMutex.ReleaseMutex() }
    $validationMutex.Dispose()
    Pop-Location
}
exit $validationExit
