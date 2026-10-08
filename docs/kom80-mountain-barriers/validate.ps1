param([ValidateSet('Focused', 'Full')][string]$Mode = 'Focused')
$ErrorActionPreference = 'Stop'
$validationRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$logRoot = Join-Path $validationRoot 'outputs/kom80'
$validationMutex = [Threading.Mutex]::new($false, 'Local\KOME-Heavy-Validation')
$acquired = $false
Push-Location -LiteralPath $validationRoot
try {
    Write-Output 'Waiting for Local\KOME-Heavy-Validation'
    while (-not $acquired) {
        try { $acquired = $validationMutex.WaitOne(1000) }
        catch [Threading.AbandonedMutexException] { $acquired = $true }
    }
    Write-Output 'Acquired Local\KOME-Heavy-Validation'
    $validationArgs = @('clean', 'test', 'build')
    if ($Mode -eq 'Focused') {
        $validationArgs = @('test')
        foreach ($suite in @(
            'kome.common.command.KOMEMountainBarrierMovementTest',
            'kome.common.command.KOMEMountainBarrierRecoveryTest',
            'kome.client.KOMEMountainContactBordersTest',
            'kome.common.command.KOMECommandTroopsMovementTest',
            'kome.common.command.KOMECampaignBoundaryMovementTest',
            'kome.common.data.KOMEConflictMovementServiceTest',
            'kome.common.data.KOMETileGameplayParityTest',
            'kome.common.data.KOMETileGameplayDefaultsTest',
            'kome.client.KOMEMapBordersTest')) {
            $validationArgs += @('--tests', $suite)
        }
    }
    $validationArgs += @('--no-daemon', '--offline', '--max-workers=2', '--console=plain')
    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    & ./gradlew.bat @validationArgs 2>&1 | Tee-Object -FilePath (Join-Path $logRoot ($Mode.ToLowerInvariant() + '.log'))
    $validationExit = $LASTEXITCODE
} finally {
    if ($acquired) { $validationMutex.ReleaseMutex() }
    $validationMutex.Dispose()
    Pop-Location
}
exit $validationExit
