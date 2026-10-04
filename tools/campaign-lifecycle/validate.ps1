param(
    [string]$Label = 'focused',
    [string[]]$GradleArgs = @('test'),
    [string]$GradleUserHome = 'C:\Users\dayne\.gradle'
)
$ErrorActionPreference = 'Stop'
$validationRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidenceRoot = Join-Path $validationRoot 'outputs/campaign-lifecycle'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
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
    $arguments = @($GradleArgs) + @('--offline', '--no-daemon', '--console=plain', '--max-workers=2')
    & ./gradlew.bat @arguments 2>&1 | Tee-Object -FilePath (Join-Path $evidenceRoot ($Label + '.log'))
    $validationExit = $LASTEXITCODE
} finally {
    if ($acquired) { $validationMutex.ReleaseMutex() }
    $validationMutex.Dispose()
    Pop-Location
}
exit $validationExit
