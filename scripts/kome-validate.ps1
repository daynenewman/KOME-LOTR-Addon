# Windows PowerShell 5.1+; uses the existing wrapper, dependency cache and build mutex.
[CmdletBinding()]
param(
    [ValidateSet('Fast','Full')][string]$Mode='Fast',
    [string[]]$Tests=@(),
    [string]$GradleUserHome=$env:GRADLE_USER_HOME,
    [ValidateRange(0,86400)][int]$LockTimeoutSeconds=900,
    [switch]$Online
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'kome-dev-common.ps1')
$validationExit=2
$held=$false
$mutex=$null
$evidence=$null
$oldCache=$env:GRADLE_USER_HOME
$oldNativePreference=Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
$pushed=$false
try {
    $repo=Get-KomeRepository $PSScriptRoot
    $stamp=(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'-'+$Mode+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
    $evidence=Join-Path $repo ('outputs/overnight-validation/'+$stamp)
    [void][IO.Directory]::CreateDirectory($evidence)
    $summary=[ordered]@{ Mode=$Mode; Started=(Get-Date -Format o); Result='FAIL'; ExitCode=2; Before=(Get-KomeState $repo); Commands=@(); Tests=$null; Artifacts=@(); Message='' }
    $summary['BeforeInputSha256']=Get-KomeInputFingerprint $repo
    if ($Mode -eq 'Full' -and $Tests.Count -gt 0) { throw 'Full always runs all tests; -Tests is valid only with Fast.' }
    if ($Mode -eq 'Fast' -and $Tests.Count -eq 0) {
        $Tests=@('kome.common.data.KOMEProgressionHardeningServiceTest','kome.common.data.KOMEServicePromotionPass3Test',
            'kome.common.data.KOMEMasterDutyReliabilityTest','kome.common.data.KOMEWorldDataSchemaTest','kome.common.network.KOMEPacketRegistrationTest')
    }
    foreach ($selector in $Tests) {
        if ($selector -notmatch '^[A-Za-z0-9_.$*]+$') { throw "Invalid/empty Gradle test selector: '$selector'" }
    }
    $wrapper=Join-Path $repo 'gradlew.bat'
    if (!(Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'Missing repository Gradle wrapper.' }
    foreach ($dependency in @('libs/LOTRMod v36.15.jar','libs/geckolib-unofficial-1.7.10-1.0.4.jar')) {
        if (!(Test-Path -LiteralPath (Join-Path $repo $dependency) -PathType Leaf)) { throw "Missing local dependency: $dependency. Do not change build configuration to compensate." }
    }
    if (!$GradleUserHome) { $GradleUserHome=Join-Path $env:USERPROFILE '.gradle' }
    $GradleUserHome=[IO.Path]::GetFullPath($GradleUserHome)
    $env:GRADLE_USER_HOME=$GradleUserHome
    $summary['GradleUserHome']=$GradleUserHome
    $summary['Offline']=!$Online
    $validationArgs=if ($Mode -eq 'Full') { @('test','build','reobfJar') } else { @('compileJava','test') }
    if ($Mode -eq 'Fast') { foreach ($selector in $Tests) { $validationArgs+=@('--tests',$selector) } }
    $validationArgs+=@('--no-daemon','--max-workers=2','--console=plain')
    if (!$Online) { $validationArgs+='--offline' }
    $summary.Commands=@(@('gradlew.bat')+$validationArgs)
    $mutex=New-Object System.Threading.Mutex($false,'Local\KOME-Heavy-Validation')
    Write-Host 'Waiting for Local\KOME-Heavy-Validation'
    $validationExit=3
    try { $held=$mutex.WaitOne([TimeSpan]::FromSeconds($LockTimeoutSeconds)) }
    catch [System.Threading.AbandonedMutexException] { $held=$true }
    if (!$held) { throw 'Timed out waiting for heavy validation. No Gradle process was started; retry after the active validation finishes.' }
    Write-Host ('Acquired validation mutex. gradlew.bat '+($validationArgs -join ' '))
    Push-Location -LiteralPath $repo
    $pushed=$true
    $validationExit=1
    # Native warnings on stderr are not failures in Windows PowerShell; use Gradle's exit code.
    $ErrorActionPreference='Continue'
    if ($oldNativePreference) { $PSNativeCommandUseErrorActionPreference=$false }
    & $wrapper @validationArgs 2>&1 | Tee-Object -FilePath (Join-Path $evidence 'gradle.log')
    $validationExit=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if ($null -eq $validationExit) { $validationExit=1 }
    $log=Get-Content -LiteralPath (Join-Path $evidence 'gradle.log') -Raw
    if ($log -match '(?m)^> Task :test(?:\s|$)' -and (Test-Path -LiteralPath (Join-Path $repo 'build/test-results/test'))) {
        Copy-Item -LiteralPath (Join-Path $repo 'build/test-results/test') -Destination (Join-Path $evidence 'test-results') -Recurse
        $counts=[ordered]@{ Suites=0; Tests=0; Passed=0; Failures=0; Errors=0; Skipped=0; SkippedTests=@() }
        foreach ($resultFile in Get-ChildItem -LiteralPath (Join-Path $evidence 'test-results') -Filter 'TEST-*.xml') {
            [xml]$xml=Get-Content -LiteralPath $resultFile.FullName -Raw
            $counts.Suites++
            foreach ($key in @('Tests','Failures','Errors','Skipped')) { $counts[$key]+=[int]$xml.testsuite.GetAttribute($key.ToLowerInvariant()) }
            foreach ($case in $xml.testsuite.SelectNodes('testcase[skipped]')) { $counts.SkippedTests+=($case.classname+'.'+$case.name) }
        }
        $counts.Passed=$counts.Tests-$counts.Failures-$counts.Errors-$counts.Skipped
        $summary.Tests=$counts
    }
    $summary['After']=Get-KomeState $repo
    $summary['AfterInputSha256']=Get-KomeInputFingerprint $repo
    if ($validationExit -eq 0 -and ($summary.BeforeInputSha256 -ne $summary.AfterInputSha256 -or
        ($summary.Before | ConvertTo-Json -Compress) -ne ($summary.After | ConvertTo-Json -Compress))) {
        $validationExit=1
        throw 'Repository changed during validation; these results cannot verify the changed state.'
    }
    if ($validationExit -eq 0 -and $Mode -eq 'Full') {
        foreach ($artifact in Get-ChildItem -LiteralPath (Join-Path $repo 'build/libs') -Filter '*.jar') {
            $summary.Artifacts+=@{ Path=$artifact.FullName; Sha256=(Get-KomeHash $artifact.FullName); Bytes=$artifact.Length }
        }
    }
    if ($validationExit -eq 0 -and ($null -eq $summary.Tests -or $summary.Tests.Tests -eq 0)) {
        $validationExit=1
        throw 'Gradle returned success without discoverable JUnit results. Inspect the log; validation is incomplete.'
    }
    if ($validationExit -ne 0) { throw "Gradle exited $validationExit. See $evidence/gradle.log" }
    $summary.Result='PASS'
} catch {
    if ($validationExit -eq 0) { $validationExit=1 }
    if ($null -ne $evidence -and (Test-Path Variable:summary)) { $summary.Message=$_.Exception.Message }
    Write-Error -ErrorAction Continue $_.Exception.Message
} finally {
    if ($pushed) { Pop-Location }
    $env:GRADLE_USER_HOME=$oldCache
    if ($oldNativePreference) { $PSNativeCommandUseErrorActionPreference=$oldNativePreference.Value }
    if ($held) { $mutex.ReleaseMutex() }
    if ($mutex) { $mutex.Dispose() }
    if ($evidence -and (Test-Path Variable:summary)) {
        $summary.ExitCode=$validationExit
        $summary['Finished']=Get-Date -Format o
        Write-KomeJson (Join-Path $evidence 'summary.json') $summary
        Write-Host "Validation evidence: $evidence"
    }
}
exit $validationExit
